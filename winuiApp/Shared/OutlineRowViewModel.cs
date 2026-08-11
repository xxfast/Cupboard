using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace Cupboard.Windows;

/// <summary>
/// One navigator row, as a plain managed object.
///
/// The Kotlin <c>WinOutlineRow</c> is a disposable handle onto native memory, so
/// it is copied out here rather than bound to directly: XAML holds these for as
/// long as the list is on screen, which is far longer than the state snapshot
/// they came from lives.
/// </summary>
public sealed class OutlineRowViewModel(
    string slideId,
    string title,
    int depth,
    int slideIndex,
    bool hasChildren,
    bool collapsed) : INotifyPropertyChanged
{
    private bool _isSelected;

    public string SlideId { get; } = slideId;
    public string Title { get; } = title;
    public int Depth { get; } = depth;
    public int SlideIndex { get; } = slideIndex;
    public bool HasChildren { get; } = hasChildren;
    public bool Collapsed { get; } = collapsed;

    /// <summary>Slide number as shown in the navigator (1-based).</summary>
    public int SlideNumber => SlideIndex + 1;

    /// <summary>Indent for nested slides, in XAML DIPs.</summary>
    public double Indent => Depth * 16.0;

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
