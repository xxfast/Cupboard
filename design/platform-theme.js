// Shared platform palettes for the Slides editor designs.
// Single source of truth: every .dc.html loads this in <helmet> and reads window.SlidesTheme.
(function () {
  const themes = {
    macOS: {
      dark: {
        bar: 'linear-gradient(#323236, #2c2c30)', barBorder: '#1a1a1c', title: '#d8d8dc', icon: '#d0d0d5', label: '#b8b8be',
        hov: 'rgba(255,255,255,0.07)', hov2: 'rgba(255,255,255,0.14)',
        insBg: '#29292d', div: '#3a3a3e', text: '#e8e8ea', subtle: '#98989f', subtle2: '#85858c',
        ctrl: '#414147', ctrlText: '#ececee', ctrlHov: '#55555c',
        segBg: '#313136', segOn: '#5c5c64', segOnText: '#ffffff', segOff: '#c8c8cc',
        track: '#4a4a50', rowBg: '#333338', rowHov: '#3c3c42', badgeOff: '#4a4a50', badgeOffText: '#d8d8dc',
        btnGrad: 'linear-gradient(#525259, #47474d)', knob: 'linear-gradient(#ffffff, #e8e8e8)',
        chrome: '#2c2c30', panel: '#28282c', border: '#3a3a3e', dim: '#a0a0a8', faint: '#6e6e76', well: '#17181c',
        accent: '#7F52FF', accentText: '#ffffff',
      },
      light: {
        bar: 'linear-gradient(#f6f5f3, #eae9e7)', barBorder: '#c8c7c5', title: '#3a3a3c', icon: '#4a4a4c', label: '#5c5c5e',
        hov: 'rgba(0,0,0,0.06)', hov2: 'rgba(0,0,0,0.1)',
        insBg: '#efeeec', div: '#d8d7d5', text: '#2a2a2c', subtle: '#7a7a7e', subtle2: '#94949a',
        ctrl: '#ffffff', ctrlText: '#2a2a2c', ctrlHov: '#e9e9e9',
        segBg: '#e1e0de', segOn: '#ffffff', segOnText: '#1d1d1f', segOff: '#5a5a5c',
        track: '#cfcecc', rowBg: '#e7e6e4', rowHov: '#dededd', badgeOff: '#c9c8c6', badgeOffText: '#3a3a3c',
        btnGrad: 'linear-gradient(#ffffff, #f1f1f1)', knob: 'linear-gradient(#ffffff, #ececec)',
        chrome: '#ececea', panel: '#f1f0ee', border: '#d6d5d3', dim: '#6a6a6e', faint: '#9a9a9e', well: '#dcdcda',
        accent: '#7F52FF', accentText: '#ffffff',
      },
      font: "-apple-system, 'SF Pro Text', 'Helvetica Neue', sans-serif", winR: '19px', thumbR: '5px', uiLabel: 'ui: SwiftUI / AppKit',
    },
    Windows: {
      dark: {
        bar: '#202020', barBorder: '#383838', title: '#ffffff', icon: '#ffffff', label: '#ffffff',
        hov: '#2d2d2d', hov2: '#323232',
        insBg: '#272727', div: '#383838', text: '#ffffff', subtle: '#9d9d9d', subtle2: '#717171',
        ctrl: '#2d2d2d', ctrlText: '#ffffff', ctrlHov: '#383838', ctrlB: '#383838', ctrlBB: '#4d4d4d',
        segBg: '#2d2d2d', segOn: '#8961ff', segOnText: '#ffffff', segOff: '#9d9d9d',
        track: '#454545', rowBg: '#2d2d2d', rowHov: '#323232', badgeOff: '#454545', badgeOffText: '#ffffff',
        btnGrad: '#2d2d2d', knob: '#ffffff',
        chrome: '#202020', panel: '#272727', border: '#383838', dim: '#9d9d9d', faint: '#717171', well: '#191919',
        accent: '#8961ff', accentText: '#ffffff',
      },
      light: {
        bar: '#f3f3f3', barBorder: '#e0e0e0', title: '#1a1a1a', icon: '#1a1a1a', label: '#1a1a1a',
        hov: '#e9e9e9', hov2: '#e0e0e0',
        insBg: '#f9f9f9', div: '#e5e5e5', text: '#1a1a1a', subtle: '#5f5f5f', subtle2: '#8a8a8a',
        ctrl: '#fbfbfb', ctrlText: '#1a1a1a', ctrlHov: '#f0f0f0', ctrlB: '#e5e5e5', ctrlBB: '#cfcfcf',
        segBg: '#ececec', segOn: '#6f42e0', segOnText: '#ffffff', segOff: '#5f5f5f',
        track: '#c8c8c8', rowBg: '#f3f3f3', rowHov: '#ebebeb', badgeOff: '#d8d8d8', badgeOffText: '#1a1a1a',
        btnGrad: '#fbfbfb', knob: '#ffffff',
        chrome: '#f3f3f3', panel: '#f9f9f9', border: '#e0e0e0', dim: '#5f5f5f', faint: '#8a8a8a', well: '#ebebeb',
        accent: '#6f42e0', accentText: '#ffffff',
      },
      font: "'Segoe UI Variable Text', 'Segoe UI', sans-serif", winR: '8px', thumbR: '4px', uiLabel: 'ui: WinUI 3 · Mica',
    },
    Linux: {
      dark: {
        bar: '#211f26', barBorder: '#47444f', title: '#e6e0e9', icon: '#cbc4d5', label: '#cbc4d5',
        hov: '#38353f', hov2: '#413e4a',
        insBg: '#211f26', div: '#47444f', text: '#e6e0e9', subtle: '#a29daa', subtle2: '#88838f',
        ctrl: '#2b2930', ctrlText: '#e6e0e9', ctrlHov: '#38353f', ctrlB: '#47444f', ctrlBB: '#47444f',
        outline: '#938f99', segBg: '#2b2930', segOn: '#4f378b', segOnText: '#e8ddff', segOff: '#cbc4d5',
        track: '#4a4458', rowBg: '#2b2930', rowHov: '#38353f', badgeOff: '#47444f', badgeOffText: '#e6e0e9',
        btnGrad: '#2b2930', knob: '#d0bcff',
        tonal: '#4a4458', tonalText: '#e8ddff',
        chrome: '#211f26', panel: '#1c1b1f', border: '#47444f', dim: '#a29daa', faint: '#736f7a', well: '#141317',
        accent: '#d0bcff', accentText: '#381e72',
      },
      light: {
        bar: '#f3edf7', barBorder: '#cac4d0', title: '#1d1b20', icon: '#49454f', label: '#49454f',
        hov: 'rgba(0,0,0,0.06)', hov2: 'rgba(0,0,0,0.1)',
        insBg: '#f3edf7', div: '#cac4d0', text: '#1d1b20', subtle: '#79747e', subtle2: '#938f99',
        ctrl: '#ffffff', ctrlText: '#1d1b20', ctrlHov: '#e8e2ec', ctrlB: '#cac4d0', ctrlBB: '#cac4d0',
        outline: '#79747e', segBg: '#ece6f0', segOn: '#e8def8', segOnText: '#1d192b', segOff: '#49454f',
        track: '#e6e0e9', rowBg: '#ece6f0', rowHov: '#e2dce8', badgeOff: '#cac4d0', badgeOffText: '#1d1b20',
        btnGrad: '#ffffff', knob: '#6750a4',
        tonal: '#e8def8', tonalText: '#1d192b',
        chrome: '#f3edf7', panel: '#fef7ff', border: '#cac4d0', dim: '#49454f', faint: '#79747e', well: '#e3ddea',
        accent: '#6750a4', accentText: '#ffffff',
      },
      font: "'Roboto', 'Ubuntu', sans-serif", winR: '16px', thumbR: '10px', uiLabel: 'ui: Compose Desktop · M3',
    },
  };

  function resolve(os, mode) {
    const base = themes[os] || themes.macOS;
    const dark = mode !== 'Light';
    return Object.assign({
      os: themes[os] ? os : 'macOS',
      dark,
      font: base.font,
      winR: base.winR,
      thumbR: base.thumbR,
      uiLabel: base.uiLabel + (dark ? ' · dark' : ' · light'),
      accentSoft: dark ? '#b9a3ff' : '#6f42e0',
    }, dark ? base.dark : base.light);
  }

  // Default sample deck — shared so every feature page shows the same content.
  const deck = [
    { title: 'Lorem ipsum' },
    { title: 'Dolor sit' },
    { title: 'Amet consectetur' },
    { title: 'Adipiscing elit', depth: 1 },
    { title: 'Sed eiusmod', depth: 2 },
    { title: 'Tempor incididunt', depth: 2 },
    { title: 'Ut labore', depth: 1 },
    { title: 'Dolore magna' },
    { title: 'Aliqua enim', depth: 1 },
    { title: 'Ad minim veniam' },
  ];

  window.SlidesTheme = { themes, resolve, deck };
})();
