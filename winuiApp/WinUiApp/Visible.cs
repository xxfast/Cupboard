using Microsoft.UI.Xaml;

namespace WinUiApp;

/// <summary>
/// Bool to <see cref="Visibility"/> for <c>{x:Bind}</c> function bindings.
///
/// A converter would do, but <c>{x:Bind ... Converter=}</c> does not compile with
/// a <c>Window</c> as the binding root: the generated resource lookup casts the
/// root to <c>FrameworkElement</c>, which a window is not. Function bindings skip
/// the lookup. The navigator's <c>{Binding}</c> template keeps
/// <see cref="BoolToVisibilityConverter"/>, which resolves at runtime and is fine.
/// </summary>
public static class Visible
{
    public static Visibility When(bool shown) =>
        shown ? Visibility.Visible : Visibility.Collapsed;

    public static Visibility Unless(bool hidden) =>
        hidden ? Visibility.Collapsed : Visibility.Visible;
}
