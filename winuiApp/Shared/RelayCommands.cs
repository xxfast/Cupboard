using System.Globalization;
using System.Windows.Input;

namespace Cupboard.Windows;

/// <summary>Minimal ICommand helpers for the WinUI host.</summary>
public sealed class RelayCommand(Action action, Func<bool>? canExecute = null) : ICommand
{
    public event EventHandler? CanExecuteChanged;

    public bool CanExecute(object? parameter) => canExecute?.Invoke() ?? true;

    public void Execute(object? parameter)
    {
        if (CanExecute(parameter)) action();
    }

    /// <summary>Re-queries <c>CanExecute</c>; the view model calls this when state lands.</summary>
    public void RaiseCanExecuteChanged() =>
        CanExecuteChanged?.Invoke(this, EventArgs.Empty);
}

/// <summary>
/// Parameterized twin, for commands that act on a value the markup names, like
/// the inspector tab strip.
/// </summary>
public sealed class RelayCommand<T>(Action<T> action, Func<T, bool>? canExecute = null) : ICommand
{
    public event EventHandler? CanExecuteChanged;

    public bool CanExecute(object? parameter) =>
        TryCoerce(parameter, out var value) && (canExecute?.Invoke(value) ?? true);

    public void Execute(object? parameter)
    {
        if (CanExecute(parameter) && TryCoerce(parameter, out var value)) action(value);
    }

    /// <summary>Re-queries <c>CanExecute</c>; the view model calls this when state lands.</summary>
    public void RaiseCanExecuteChanged() =>
        CanExecuteChanged?.Invoke(this, EventArgs.Empty);

    /// <summary>
    /// A plain <c>CommandParameter="0"</c> attribute reaches here as a string,
    /// because XAML has no type to parse it against on an <c>ICommand</c>. Convert
    /// instead of making every call site spell out an <c>x:Int32</c> element.
    /// </summary>
    private static bool TryCoerce(object? parameter, out T value)
    {
        if (parameter is T typed)
        {
            value = typed;
            return true;
        }

        try
        {
            value = (T)Convert.ChangeType(parameter, typeof(T), CultureInfo.InvariantCulture)!;
            return true;
        }
        catch (Exception exception) when (
            exception is InvalidCastException or FormatException or OverflowException)
        {
            value = default!;
            return false;
        }
    }
}
