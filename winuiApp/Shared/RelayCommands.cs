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
