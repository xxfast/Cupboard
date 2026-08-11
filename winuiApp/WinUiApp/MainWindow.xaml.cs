using System.ComponentModel;
using Cupboard.Windows;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace WinUiApp;

public sealed partial class MainWindow : Window
{
    public MainWindow()
    {
        // Before the view model: it captures the current context to marshal
        // shared-state updates back onto the UI thread.
        SynchronizationContext.SetSynchronizationContext(
            new DispatcherQueueSynchronizationContext(DispatcherQueue));

        ViewModel = new EditorViewModel();
        InitializeComponent();

        ViewModel.PropertyChanged += ViewModelOnPropertyChanged;

        Closed += async (_, _) =>
        {
            ViewModel.PropertyChanged -= ViewModelOnPropertyChanged;
            await ViewModel.DisposeAsync();
        };
    }

    public EditorViewModel ViewModel { get; }

    private void ViewModelOnPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        // The shared state decides what is selected; push it at the list without
        // bouncing back through SelectionChanged.
        if (e.PropertyName is nameof(EditorViewModel.SelectedRow) or null &&
            !ReferenceEquals(OutlineList.SelectedItem, ViewModel.SelectedRow))
        {
            OutlineList.SelectedItem = ViewModel.SelectedRow;
        }
    }

    private void OutlineList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (OutlineList.SelectedItem is OutlineRowViewModel row)
            ViewModel.SelectedRow = row;
    }

    // Closing the window is the window's business, not the shared editor's.
    private void ExitMenuItem_Click(object sender, RoutedEventArgs e) => Close();
}
