using System.Collections.ObjectModel;
using System.ComponentModel;
using System.IO;
using System.Runtime.CompilerServices;
using System.Windows.Input;
using Kotlin = Cupboard.Kotlin.Winui;

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
    private readonly Kotlin.WinEditorViewModel _kotlinViewModel;
    private readonly Task _observation;
    private OutlineRowViewModel? _selectedRow;
    private string _selectedSlideTitle = string.Empty;
    private bool _canUndo;
    private bool _canRedo;
    private int _disposed;

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
        Kotlin.WindowsApp.Bootstrap(storage);

        _kotlinViewModel = new Kotlin.WinEditorViewModel();

        UndoCommand = new RelayCommand(_kotlinViewModel.OnUndo, () => CanUndo);
        RedoCommand = new RelayCommand(_kotlinViewModel.OnRedo, () => CanRedo);
        ToggleCollapsedCommand = new RelayCommand<OutlineRowViewModel>(
            row => _kotlinViewModel.OnToggleCollapsed(row.SlideId));

        // Enumerating is also what starts the shared presenter: its state flow is
        // lazily shared, so nothing runs until this subscribes.
        _observation = ObserveStatesAsync();
    }

    public ObservableCollection<OutlineRowViewModel> Outline { get; } = [];

    public ICommand UndoCommand { get; }
    public ICommand RedoCommand { get; }
    public ICommand ToggleCollapsedCommand { get; }

    public string SelectedSlideTitle
    {
        get => _selectedSlideTitle;
        private set => SetField(ref _selectedSlideTitle, value);
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

    /// <summary>Row selected in the navigator. Setting it drives the shared editor.</summary>
    public OutlineRowViewModel? SelectedRow
    {
        get => _selectedRow;
        set
        {
            if (ReferenceEquals(_selectedRow, value)) return;
            _selectedRow = value;
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(SelectedRow)));
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

    private void Apply(Kotlin.WinEditorState state)
    {
        SelectedSlideTitle = state.SelectedSlideTitle;
        CanUndo = state.CanUndo;
        CanRedo = state.CanRedo;

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
                    row.Collapsed);
                projected.IsSelected = projected.SlideId == state.SelectedSlideId;
                Outline.Add(projected);
                if (projected.IsSelected) selected = projected;
            }
        }

        // Assign the field, not the property: this is the shared state telling us
        // what is selected, so echoing it back as an event would be a loop.
        if (!ReferenceEquals(_selectedRow, selected))
        {
            _selectedRow = selected;
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(SelectedRow)));
        }
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
