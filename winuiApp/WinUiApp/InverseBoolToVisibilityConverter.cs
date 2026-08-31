using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Data;

namespace WinUiApp;

/// <summary>
/// Bool to <see cref="Visibility"/>, inverted.
///
/// The inspector tab strip shows one of two TextBlocks per tab, an active and an
/// inactive one, because that keeps both colours on <c>ThemeResource</c> and so
/// following the theme. Swapping them needs the negative of
/// <see cref="BoolToVisibilityConverter"/>.
/// </summary>
public sealed class InverseBoolToVisibilityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language) =>
        value is true ? Visibility.Collapsed : Visibility.Visible;

    public object ConvertBack(object value, Type targetType, object parameter, string language) =>
        value is not Visibility.Visible;
}
