using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Windows.Input;

namespace Cupboard.Windows;

/// <summary>
/// One navigator row, as a plain managed object.
///
/// The Kotlin <c>WinOutlineRow</c> is a disposable handle onto native memory, so
/// it is copied out here rather than bound to directly: XAML holds these for as
/// long as the list is on screen, which is far longer than the state snapshot
/// they came from lives.
///
/// Rows are immutable apart from <see cref="IsSelected"/>: every state snapshot
/// rebuilds the whole outline, so a row never has to change shape in place.
/// </summary>
public sealed class OutlineRowViewModel(
    string slideId,
    string title,
    int depth,
    int slideIndex,
    bool hasChildren,
    bool collapsed,
    Action<string> toggleCollapsed) : INotifyPropertyChanged
{
    /// <summary>Segoe Fluent Icons chevrons: right when collapsed, down when open.</summary>
    private const string ChevronRightGlyph = "\uE76C";
    private const string ChevronDownGlyph = "\uE70D";

    private bool _isSelected;

    public string SlideId { get; } = slideId;
    public string Title { get; } = title;
    public int Depth { get; } = depth;
    public int SlideIndex { get; } = slideIndex;
    public bool HasChildren { get; } = hasChildren;
    public bool Collapsed { get; } = collapsed;

    /// <summary>
    /// Toggles this row's group. Lives on the row rather than on the editor so the
    /// navigator template can bind it with no command parameter and no reach into
    /// the window's namescope, which is unreliable inside a DataTemplate.
    /// </summary>
    public ICommand ToggleCollapsedCommand { get; } =
        new RelayCommand(() => toggleCollapsed(slideId));

    /// <summary>Slide number as shown in the navigator (1-based).</summary>
    public int SlideNumber => SlideIndex + 1;

    /// <summary>Indent for nested slides, in XAML DIPs. 18 per level on Windows.</summary>
    public double Indent => Depth * 18.0;

    /// <summary>Disclosure glyph. Only shown when <see cref="HasChildren"/>.</summary>
    public string ChevronGlyph => Collapsed ? ChevronRightGlyph : ChevronDownGlyph;

    public bool IsSelected
    {
        get => _isSelected;
        set => SetField(ref _isSelected, value);
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    private void SetField<T>(ref T field, T value, [CallerMemberName] string? propertyName = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value)) return;
        field = value;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
    }
}
