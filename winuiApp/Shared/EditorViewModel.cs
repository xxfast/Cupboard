using System.Collections.ObjectModel;
using System.ComponentModel;
using System.IO;
using System.Runtime.CompilerServices;
using System.Windows.Input;
using KotlinApp = Cupboard.Kotlin.Winui;

namespace Cupboard.Windows;

/// <summary>
/// Managed adapter over the shared Kotlin editor.
///
/// All it does is translate: it enumerates the Kotlin <c>StateFlow</c>, marshals
/// each snapshot onto the UI <see cref="SynchronizationContext"/>, and projects it
/// into the <see cref="INotifyPropertyChanged"/> shapes XAML binds to. No editor
/// state lives here; it all belongs to the shared view model.
/// </summary>
public sealed class EditorViewModel : INotifyPropertyChanged, IAsyncDisposable
{
    private readonly SynchronizationContext _ui;
    private readonly CancellationTokenSource _cancellation = new();
    private readonly KotlinApp.WinEditorViewModel _kotlinViewModel;
    private readonly Task _observation;
    private readonly Action<string> _toggleCollapsed;
    private OutlineRowViewModel? _selectedRow;
    private string _selectedSlideTitle = string.Empty;
    private string _slidePosition = string.Empty;
    private string _toggleCollapsedLabel = CollapseLabel;
    private bool _canUndo;
    private bool _canRedo;
    private int _disposed;

    private const string CollapseLabel = "Collapse Slide";
    private const string ExpandLabel = "Expand Slide";

    /// <param name="uiContext">
    /// UI synchronization context. Defaults to <see cref="SynchronizationContext.Current"/>,
    /// so construct on the UI thread or pass one explicitly.
    /// </param>
    public EditorViewModel(SynchronizationContext? uiContext = null)
    {
        _ui = uiContext ?? SynchronizationContext.Current
            ?? throw new InvalidOperationException(
                "Create on the UI thread or pass a SynchronizationContext.");

        // The Kotlin side owns the document file; it only needs to be told where.
        var storage = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Cupboard");
        Directory.CreateDirectory(storage);
        KotlinApp.WindowsApp.Bootstrap(storage);

        _kotlinViewModel = new KotlinApp.WinEditorViewModel();
        _toggleCollapsed = id => _kotlinViewModel.OnToggleCollapsed(id);

        UndoCommand = new RelayCommand(_kotlinViewModel.OnUndo, () => CanUndo);
        RedoCommand = new RelayCommand(_kotlinViewModel.OnRedo, () => CanRedo);
        // Menu-facing twin of the per-row chevron command: the Slide menu acts on
        // whatever is selected, so it takes no parameter and greys out when the
        // selection has nothing to fold.
        ToggleSelectedCollapsedCommand = new RelayCommand(
            () =>
            {
                if (SelectedRow is { HasChildren: true } row) _toggleCollapsed(row.SlideId);
            },
            () => SelectedRow?.HasChildren == true);

        // Enumerating is also what starts the shared presenter: its state flow is
        // lazily shared, so nothing runs until this subscribes.
        _observation = ObserveStatesAsync();
    }

    public ObservableCollection<OutlineRowViewModel> Outline { get; } = [];

    public ICommand UndoCommand { get; }
    public ICommand RedoCommand { get; }
    public ICommand ToggleSelectedCollapsedCommand { get; }

    public string SelectedSlideTitle
    {
        get => _selectedSlideTitle;
        private set => SetField(ref _selectedSlideTitle, value);
    }

    /// <summary>Status bar position, e.g. "slide 4 of 8". Counts the whole deck.</summary>
    public string SlidePosition
    {
        get => _slidePosition;
        private set => SetField(ref _slidePosition, value);
    }

    /// <summary>Label for the Slide menu item, which folds or unfolds the selection.</summary>
    public string ToggleCollapsedLabel
    {
        get => _toggleCollapsedLabel;
        private set => SetField(ref _toggleCollapsedLabel, value);
    }

    public bool CanUndo
    {
        get => _canUndo;
        private set
        {
            if (!SetField(ref _canUndo, value)) return;
            ((RelayCommand)UndoCommand).RaiseCanExecuteChanged();
        }
    }

    public bool CanRedo
    {
        get => _canRedo;
        private set
        {
            if (!SetField(ref _canRedo, value)) return;
            ((RelayCommand)RedoCommand).RaiseCanExecuteChanged();
        }
    }

    /// <summary>
    /// Row selected in the navigator. Setting it drives the shared editor; the
    /// shared editor moving the selection raises <see cref="PropertyChanged"/> for
    /// it, so the round trip works in both directions.
    /// </summary>
    public OutlineRowViewModel? SelectedRow
    {
        get => _selectedRow;
        set
        {
            if (ReferenceEquals(_selectedRow, value)) return;
            SetSelectedRow(value);
            if (value is not null) _kotlinViewModel.OnSelectSlide(value.SlideId);
        }
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    private async Task ObserveStatesAsync()
    {
        try
        {
            await foreach (var state in _kotlinViewModel.States.WithCancellation(_cancellation.Token))
            {
                using (state)
                {
                    await RunOnUiAsync(() => Apply(state));
                }
            }
        }
        catch (OperationCanceledException) when (_cancellation.IsCancellationRequested)
        {
            // Window shutdown cancels the generated KotlinFlow collection.
        }
    }

    private void Apply(KotlinApp.WinEditorState state)
    {
        SelectedSlideTitle = state.SelectedSlideTitle;
        CanUndo = state.CanUndo;
        CanRedo = state.CanRedo;
        SlidePosition = state.SlideCount > 0 && state.SelectedSlideIndex >= 0
            ? $"slide {state.SelectedSlideIndex + 1} of {state.SlideCount}"
            : "no slides";

        // Rebuilt wholesale: the outline is short and collapsing reshapes it, so
        // diffing would cost more than it saves.
        Outline.Clear();
        OutlineRowViewModel? selected = null;
        foreach (var row in state.Outline)
        {
            using (row)
            {
                var projected = new OutlineRowViewModel(
                    row.SlideId,
                    row.Title,
                    row.Depth,
                    row.SlideIndex,
                    row.HasChildren,
                    row.Collapsed,
                    _toggleCollapsed);
                projected.IsSelected = projected.SlideId == state.SelectedSlideId;
                Outline.Add(projected);
                if (projected.IsSelected) selected = projected;
            }
        }

        // Go through the field, not the property: this is the shared state telling
        // us what is selected, so echoing it back at Kotlin would be a loop.
        if (!ReferenceEquals(_selectedRow, selected)) SetSelectedRow(selected);
    }

    /// <summary>
    /// Moves the selection without touching the shared editor, and refreshes
    /// everything derived from it: the Slide menu's label and its enablement.
    /// </summary>
    private void SetSelectedRow(OutlineRowViewModel? row)
    {
        _selectedRow = row;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(SelectedRow)));
        ToggleCollapsedLabel = row is { HasChildren: true, Collapsed: true }
            ? ExpandLabel
            : CollapseLabel;
        ((RelayCommand)ToggleSelectedCollapsedCommand).RaiseCanExecuteChanged();
    }

    public async ValueTask DisposeAsync()
    {
        if (Interlocked.Exchange(ref _disposed, 1) != 0) return;

        _cancellation.Cancel();
        _kotlinViewModel.Close();
        try { await _observation; }
        catch (OperationCanceledException) { }
        finally
        {
            await _kotlinViewModel.DisposeAsync();
            _cancellation.Dispose();
        }
    }

    private Task RunOnUiAsync(Action action)
    {
        if (SynchronizationContext.Current == _ui)
        {
            action();
            return Task.CompletedTask;
        }

        var tcs = new TaskCompletionSource();
        _ui.Post(_ =>
        {
            try
            {
                action();
                tcs.SetResult();
            }
            catch (Exception ex)
            {
                tcs.SetException(ex);
            }
        }, null);
        return tcs.Task;
    }

    private bool SetField<T>(ref T field, T value, [CallerMemberName] string? propertyName = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value)) return false;
        field = value;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
        return true;
    }
}
