using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Data;

namespace WinUiApp;

/// <summary>
/// Bool to <see cref="Visibility"/>.
///
/// Needed because the navigator template binds with <c>{Binding}</c>, which has no
/// implicit bool conversion (only <c>{x:Bind}</c> does), and because the shared
/// view models live in a project that does not reference WinUI, so they cannot
/// hand out <see cref="Visibility"/> themselves.
/// </summary>
public sealed class BoolToVisibilityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language) =>
        value is true ? Visibility.Visible : Visibility.Collapsed;

    public object ConvertBack(object value, Type targetType, object parameter, string language) =>
        value is Visibility.Visible;
}
