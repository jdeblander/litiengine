package de.gurkenlabs.utiliti.swing;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;

import com.github.weisj.darklaf.LafManager;
import com.github.weisj.darklaf.theme.DarculaTheme;
import com.github.weisj.darklaf.theme.IntelliJTheme;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.GameListener;
import de.gurkenlabs.litiengine.resources.Resources;
import de.gurkenlabs.utiliti.Cursors;
import de.gurkenlabs.utiliti.Style;
import de.gurkenlabs.utiliti.Style.Theme;
import de.gurkenlabs.utiliti.components.Controller;
import de.gurkenlabs.utiliti.components.Editor;
import de.gurkenlabs.utiliti.components.EntityController;
import de.gurkenlabs.utiliti.components.LayerController;
import de.gurkenlabs.utiliti.components.MapComponent;
import de.gurkenlabs.utiliti.components.MapController;
import de.gurkenlabs.utiliti.components.PropertyInspector;
import de.gurkenlabs.utiliti.swing.controllers.AssetList;
import de.gurkenlabs.utiliti.swing.controllers.EntityList;
import de.gurkenlabs.utiliti.swing.controllers.LayerList;
import de.gurkenlabs.utiliti.swing.controllers.MapList;
import de.gurkenlabs.utiliti.swing.menus.CanvasPopupMenu;
import de.gurkenlabs.utiliti.swing.menus.MainMenuBar;
import de.gurkenlabs.utiliti.swing.panels.MapObjectInspector;

public final class UI {
  private static final Logger log = Logger.getLogger(UI.class.getName());
  private static final int SCROLL_MAX = 100;
  private static JPanel renderPanel;
  private static JScrollBar horizontalScroll;
  private static JScrollBar verticalScroll;
  private static JPopupMenu canvasPopup;
  private static AssetList assetComponent;

  private static MapObjectInspector mapObjectPanel;
  private static MapList mapSelectionPanel;
  private static LayerList mapLayerList;
  private static EntityList entityList;

  private static boolean initialized;

  private static float currentScrollSize;

  private UI() {
  }

  public static boolean notifyPendingChanges() {
    String resourceFile = Editor.instance().getCurrentResourceFile() != null ? Editor.instance().getCurrentResourceFile() : "";
    if (Editor.instance().getChangedMaps().isEmpty() && !Editor.instance().isUnsavedProject()) {
      return true;
    }

    int n = JOptionPane.showConfirmDialog(Game.window().getRenderComponent(), Resources.strings().get("hud_saveProjectMessage") + "\n" + resourceFile, Resources.strings().get("hud_saveProject"), JOptionPane.YES_NO_CANCEL_OPTION);

    if (n == JOptionPane.YES_OPTION) {
      Editor.instance().save(false);
    }

    return n != JOptionPane.CANCEL_OPTION && n != JOptionPane.CLOSED_OPTION;
  }

  public static boolean showRevertWarning() {
    int n = JOptionPane.showConfirmDialog(Game.window().getRenderComponent(), Resources.strings().get("hud_revertChangesMessage"), Resources.strings().get("hud_revertChanges"), JOptionPane.YES_NO_OPTION);
    return n == JOptionPane.YES_OPTION;
  }

  public static synchronized void init() {
    if (initialized) {
      return;
    }

    Game.screens().display(Editor.instance());

    javax.swing.JComponent.setDefaultLocale(Locale.getDefault());
    JPopupMenu.setDefaultLightWeightPopupEnabled(false);
    UIManager.getDefaults().put("SplitPane.border", BorderFactory.createEmptyBorder());
    setDefaultSwingFont(Style.getDefaultFont());

    Tray.init();
    Game.window().cursor().set(Cursors.DEFAULT, 0, 0);
    Game.window().cursor().setOffsetX(0);
    Game.window().cursor().setOffsetY(0);
    setupInterface();
    Game.window().getHostControl().revalidate();
    Game.window().getRenderComponent().setBackground(new Color(30, 31, 32));

    loadTheme(Editor.preferences().getTheme());

    initialized = true;
  }

  public static PropertyInspector getInspector() {
    return mapObjectPanel;
  }

  public static LayerController getLayerController() {
    return mapLayerList;
  }

  public static EntityController getEntityController() {
    return entityList;
  }

  public static Controller getAssetController() {
    return assetComponent;
  }

  public static MapController getMapController() {
    return mapSelectionPanel;
  }

  private static void updateScrollBars() {
    if (Game.world().environment() == null) {
      return;
    }

    double relativeX = Game.world().camera().getFocus().getX() / Game.world().environment().getMap().getSizeInPixels().width;
    double relativeY = Game.world().camera().getFocus().getY() / Game.world().environment().getMap().getSizeInPixels().height;

    // decouple the scrollbar from the environment
    currentScrollSize = Math.round(SCROLL_MAX * Math.sqrt(Game.world().camera().getRenderScale()));

    horizontalScroll.setMinimum(0);
    horizontalScroll.setMaximum((int) currentScrollSize);
    verticalScroll.setMinimum(0);
    verticalScroll.setMaximum((int) currentScrollSize);

    int valueX = (int) (relativeX * currentScrollSize);
    int valueY = (int) (relativeY * currentScrollSize);

    horizontalScroll.setValue(valueX);
    verticalScroll.setValue(valueY);
  }

  private static double getScrollValue(JScrollBar scrollbar, double actualSize) {
    double currentValue = scrollbar.getValue() / currentScrollSize;
    return currentValue * actualSize;
  }

  private static void initScrollBars(JPanel renderPane) {
    horizontalScroll = new JScrollBar(java.awt.Adjustable.HORIZONTAL);
    renderPane.add(horizontalScroll, BorderLayout.SOUTH);
    verticalScroll = new JScrollBar(java.awt.Adjustable.VERTICAL);
    renderPane.add(verticalScroll, BorderLayout.EAST);

    horizontalScroll.setDoubleBuffered(true);
    verticalScroll.setDoubleBuffered(true);

    horizontalScroll.addAdjustmentListener(e -> {
      if (Editor.instance().getMapComponent().isLoading()) {
        return;
      }

      final double x = getScrollValue(horizontalScroll, Game.world().environment().getMap().getSizeInPixels().width);

      Point2D newFocus = new Point2D.Double(x, Game.world().camera().getFocus().getY());
      Game.world().camera().setFocus(newFocus);
    });

    verticalScroll.addAdjustmentListener(e -> {
      if (Editor.instance().getMapComponent().isLoading()) {
        return;
      }

      final double y = getScrollValue(verticalScroll, Game.world().environment().getMap().getSizeInPixels().height);

      Point2D newFocus = new Point2D.Double(Game.world().camera().getFocus().getX(), y);
      Game.world().camera().setFocus(newFocus);
    });

    Game.world().camera().onZoom(e -> {
      updateScrollBars();
    });

    Game.world().camera().onFocus(e -> {
      updateScrollBars();
    });

    Game.world().onLoaded(e -> {
      updateScrollBars();
    });
  }

  private static void setupInterface() {
    JFrame window = initWindow();

    Canvas canvas = Game.window().getRenderComponent();
    canvas.setFocusable(true);
    canvas.setSize((int) (window.getSize().width * 0.75), window.getSize().height);

    // remove canvas because we want to add a wrapping panel
    window.remove(canvas);

    renderPanel = new JPanel(new BorderLayout());
    renderPanel.add(canvas);
    renderPanel.setMinimumSize(new Dimension(300, 100));
    initScrollBars(renderPanel);

    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, initRenderSplitPanel(renderPanel, window), initRightSplitPanel());
    split.setContinuousLayout(true);
    split.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        Editor.preferences().setWidth(window.getWidth());
        Editor.preferences().setHeight(window.getHeight());
      }
    });

    split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> Editor.preferences().setMainSplitter(split.getDividerLocation()));

    JPanel rootPanel = new JPanel(new BorderLayout());
    window.setContentPane(rootPanel);

    rootPanel.add(split, BorderLayout.CENTER);
    split.setDividerLocation(Editor.preferences().getMainSplitterPosition() != 0 ? Editor.preferences().getMainSplitterPosition() : (int) (window.getSize().width * 0.75));

    initPopupMenu(canvas);
    window.setJMenuBar(new MainMenuBar());
  }

  private static JFrame initWindow() {
    JFrame window = ((JFrame) Game.window().getHostControl());
    window.setResizable(true);

    Game.addGameListener(new GameListener() {
      @Override
      public boolean terminating() {
        boolean terminate = notifyPendingChanges();
        if (terminate) {
          Editor.preferences().setFrameState(window.getExtendedState());
        }

        return terminate;
      }
    });

    window.setLocationRelativeTo(null);
    if (Editor.preferences().getFrameState() != java.awt.Frame.ICONIFIED && Editor.preferences().getFrameState() != java.awt.Frame.NORMAL) {
      window.setExtendedState(Editor.preferences().getFrameState());
    } else if (Editor.preferences().getWidth() != 0 && Editor.preferences().getHeight() != 0) {
      window.setSize(Editor.preferences().getWidth(), Editor.preferences().getHeight());
    }

    return window;
  }

  private static Component initRenderSplitPanel(JPanel renderPanel, JFrame window) {
    JSplitPane renderSplitPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT, renderPanel, initBottomPanel());
    if (Editor.preferences().getBottomSplitter() != 0) {
      renderSplitPanel.setDividerLocation(Editor.preferences().getBottomSplitter());
    } else {
      renderSplitPanel.setDividerLocation((int) (window.getSize().height * 0.75));
    }

    renderSplitPanel.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> Editor.preferences().setBottomSplitter(renderSplitPanel.getDividerLocation()));
    renderSplitPanel.setContinuousLayout(true);
    return renderSplitPanel;
  }

  private static Component initRightSplitPanel() {
    mapSelectionPanel = new MapList();

    mapLayerList = new LayerList();
    entityList = new EntityList();
    JTabbedPane tabPane = new JTabbedPane();
    tabPane.setFont(Style.getHeaderFont());
    tabPane.add(entityList);
    tabPane.add(mapLayerList);
    tabPane.setMaximumSize(new Dimension(0, 150));

    JSplitPane topRightSplitPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    topRightSplitPanel.setContinuousLayout(true);
    topRightSplitPanel.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> Editor.preferences().setMapPanelSplitter(topRightSplitPanel.getDividerLocation()));
    if (Editor.preferences().getMapPanelSplitter() != 0) {
      topRightSplitPanel.setDividerLocation(Editor.preferences().getMapPanelSplitter());
    }

    topRightSplitPanel.setLeftComponent(mapSelectionPanel);
    topRightSplitPanel.setRightComponent(tabPane);

    mapObjectPanel = new MapObjectInspector();

    JSplitPane rightSplitPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    rightSplitPanel.setMinimumSize(new Dimension(320, 0));
    rightSplitPanel.setBottomComponent(mapObjectPanel);
    rightSplitPanel.setTopComponent(topRightSplitPanel);
    rightSplitPanel.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> Editor.preferences().setSelectionEditSplitter(rightSplitPanel.getDividerLocation()));
    if (Editor.preferences().getSelectionEditSplitter() != 0) {
      rightSplitPanel.setDividerLocation(Editor.preferences().getSelectionEditSplitter());
    }

    return rightSplitPanel;
  }

  private static JPanel initBottomPanel() {
    JPanel bottomPanel = new JPanel(new BorderLayout());
    JTabbedPane bottomTab = new JTabbedPane();
    bottomTab.setFont(Style.getHeaderFont());

    assetComponent = new AssetList();
    bottomTab.addTab(Resources.strings().get("assettree_assets"), Icons.ASSET, assetComponent);
    bottomTab.addTab(Resources.strings().get("assettree_console"), Icons.CONSOLE, new ConsoleComponent());

    bottomPanel.add(StatusBar.create(), BorderLayout.NORTH);
    bottomPanel.add(bottomTab, BorderLayout.CENTER);

    return bottomPanel;
  }

  private static void initPopupMenu(Canvas canvas) {
    canvasPopup = new CanvasPopupMenu();

    canvas.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) {
          Editor.instance().getMapComponent().setEditMode(MapComponent.EDITMODE_EDIT);
          canvasPopup.show(canvas, e.getX(), e.getY());
        }
      }
    });
  }

  public static void initLookAndFeel() {
    LafManager.setDecorationsEnabled(true);
    LafManager.install(new DarculaTheme());
  }

  public static synchronized void loadTheme(Theme theme) {
    SwingUtilities.invokeLater(() -> {
      setDefaultForegroundColor(theme);
      switch (theme) {
      case DARK:
        LafManager.install(new DarculaTheme());
        break;
      case LIGHT:
        LafManager.install(new IntelliJTheme());
        break;
      default:
        break;
      }
    });
  }

  private static void setDefaultSwingFont(Font font) {
    Enumeration<Object> keys = UIManager.getDefaults().keys();
    while (keys.hasMoreElements()) {
      Object key = keys.nextElement();

      Object value = UIManager.get(key);
      if (value instanceof javax.swing.plaf.FontUIResource) {
        UIManager.put(key, new FontUIResource(font));
      }
    }
  }

  private static void setDefaultForegroundColor(Theme theme) {
    final List<String> foregroundKeys = Arrays.asList("FileView.foreground", "Icons.arrowRightSelected.color", "Icons.arrowRight.color", "ComboBox.selectionForeground", "Icons.speaker2.color", "Tree.selectionForeground", "Icons.verticalGlue.color", "Slider.foreground",
        "Windows.OptionPane.questionDialog.titlePane.foreground", "Icons.copy.color", "textForeground", "Icons.moveToLeftBottom.color", "Tree.textForeground", "ColorChooser.foreground", "List.selectionForeground", "TextField.selectionForeground", "Table.selectionForegroundInactive",
        "ToolBar.dockingForeground", "Icons.speaker0.crossColor", "CheckBox.foreground", "Icons.minus.color", "Icons.frame.color", "Table.foreground", "Viewport.foreground", "FormattedTextField.caretForeground", "Icons.speaker4.color", "MenuBar.foreground", "OptionPane.foreground",
        "Icons.speaker3.color", "Icons.speaker0.color", "ComboBox.foreground", "ToolTip.foreground", "Icons.eye.color", "Icons.speaker3.volumeColor", "textForegroundDefault", "Icons.speaker1.color", "OptionPane.warningDialog.titlePane.foreground", "TaskPane.titleForeground", "Icons.moreTabs.color",
        "Icons.minusSelected.color", "Icons.moveToTopLeft.color", "Icons.moveToBottomLeft.color", "global.caretForeground", "Icons.moveToBottomRight.color", "TextField.foreground", "Icons.separatorH.color", "Icons.newFolder.color", "ToolBar.foreground", "PasswordField.caretForeground",
        "PasswordField.foreground", "TabFrameTab.foreground", "Icons.arrowDownSort.color", "Icons.plusSelected.color", "Icons.add.color", "Icons.cut.color", "TextPane.caretForeground", "global.selectionForeground", "Button.foreground", "Icons.save.color", "Icons.listFiles.color", "Label.foreground",
        "EditorPane.caretForeground", "TabbedPane.foreground", "PopupMenu.foreground", "CheckBoxMenuItem.foreground", "activeCaptionText", "TextArea.selectionForeground", "menuIconSelected", "Icons.arrowUp.color", "Icons.arrowsUpDownSort.color", "Icons.delete.color", "Icons.separatorV.color",
        "RadioButtonMenuItem.foreground", "CheckBoxMenuItem.selectionForeground", "Icons.verticalGrip.color", "Icons.arrowSplitCenterV.color", "Icons.moveToRightBottom.color", "PasswordField.selectionForeground", "TaskPane.titleOver", "Tree.foreground", "Icons.arrowUpSelected.color",
        "List.selectionForegroundInactive", "TitledBorder.titleColor", "Icons.pipette.color", "global.textForeground", "TextField.caretForeground", "Button.selectedButtonForeground", "ScrollBar.foreground", "TextArea.foreground", "textSelectionForeground", "Menu.selectionForeground",
        "FormattedTextField.selectionForeground", "Icons.moveToTopRight.color", "NumberingPane.currentLineForeground", "Panel.foreground", "global.foreground", "Icons.close.color", "OptionPane.errorDialog.titlePane.foreground", "RadioButtonMenuItem.selectionForeground", "Icons.moreTabs.arrowColor",
        "RadioButton.foreground", "Icons.paste.color", "Icons.homeFolder.color", "ScrollPane.foreground", "ToggleButton.foreground", "MenuItem.foreground", "TaskPane.specialTitleBackground", "Icons.arrowLeft.color", "TextArea.caretForeground", "List.foreground", "Icons.searchWithHistory.color",
        "Table.selectionForeground", "Icons.plus.color", "Icons.moveToLeftTop.color", "Icons.arrowSplitCenterH.color", "global.selectionForegroundInactive", "Icons.arrowUpSort.color", "DesktopIcon.foreground", "ProgressBar.selectionBackground", "Icons.arrowDownSelected.color",
        "Icons.speaker4.volumeColor", "Icons.searchWithHistory.arrowColor", "caret", "Menu.foreground", "EditorPane.foreground", "Table.focusCellForeground", "Spinner.foreground", "textSelectionForegroundInactive", "TextPane.selectionForeground",
        "Windows.OptionPane.warningDialog.titlePane.foreground", "InternalFrameTitlePane.selectedTextForeground", "Label.cellForegroundNoFocus", "MenuItem.selectionForeground", "OptionPane.messageForeground", "Icons.speaker2.volumeColor", "Icons.clear.color", "TaskPane.specialTitleOver",
        "FormattedTextField.foreground", "TaskPane.foreground", "Icons.desktop.color", "EditorPane.selectionForeground", "menuIconEnabled", "Icons.arrowDivider.color", "Icons.search.color", "Icons.moveToRightTop.color", "TaskPane.specialTitleForeground", "Icons.collapse.color",
        "Icons.horizontalGlue.color", "ProgressBar.foreground", "ToolBar.floatingForeground", "TableHeader.foreground", "Icons.horizontalGrip.color", "Tree.selectionForegroundInactive", "Icons.arrowDown.color", "Icons.arrowLeftSelected.color", "OptionPane.questionDialog.titlePane.foreground",
        "Windows.OptionPane.errorDialog.titlePane.foreground", "Icons.groupBy.color", "Icons.speaker1.volumeColor", "TextPane.foreground");

    ColorUIResource color = new ColorUIResource(theme == Theme.DARK ? Style.COLOR_DARKTHEME_FOREGROUND : Style.COLOR_LIGHTTHEME_FOREGROUND);

    Enumeration<Object> keys = UIManager.getDefaults().keys();
    while (keys.hasMoreElements()) {
      Object key = keys.nextElement();

      Object value = UIManager.get(key);
      if (value instanceof javax.swing.plaf.ColorUIResource && foregroundKeys.contains(key)) {
        UIManager.put(key, new ColorUIResource(color));
      }
    }
  }
}
