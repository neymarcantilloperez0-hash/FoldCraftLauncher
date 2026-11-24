package com.tungsten.fcl.control;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.tungsten.fcl.setting.MenuSetting;
import com.tungsten.fclauncher.keycodes.FCLKeycodes;
import com.tungsten.fclcore.task.Schedulers;

/**
 * SmLauncher: botón flotante "SM" y panel de cliente dentro del juego.
 */
public class SmClientOverlay {

    private static final int PANEL_COLOR = Color.parseColor("#2A1B4A");
    private static final int ACCENT_COLOR = Color.parseColor("#7C4DFF");
    private static final int CARD_COLOR = Color.parseColor("#80382560");
    private static final int RIPPLE_COLOR = Color.parseColor("#40FFFFFF");
    private static final int SCRIM_COLOR = Color.parseColor("#99000000");

    private final GameMenu menu;
    private final FrameLayout scrim;
    private Switch fpsSwitch;
    private Switch memorySwitch;
    private Switch gyroSwitch;
    private Switch hideSwitch;
    private boolean syncing = false;

    private interface BoolAction {
        void run(boolean checked);
    }

    public SmClientOverlay(GameMenu menu) {
        this.menu = menu;
        this.scrim = buildScrim();
        RelativeLayout base = menu.getBaseLayout();
        base.addView(scrim, new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // El botón se añade al final para quedar siempre por encima del panel
        base.addView(buildFloatingButton(), floatingButtonParams());
    }

    // ---------------------------------------------------------------- abrir / cerrar

    private void togglePanel() {
        if (scrim.getVisibility() == View.VISIBLE) {
            closePanel();
        } else {
            openPanel();
        }
    }

    private void openPanel() {
        // Refresca los interruptores por si se cambiaron desde el menú normal de FCL
        MenuSetting setting = menu.getMenuSetting();
        setChecked(fpsSwitch, setting.isShowFps());
        setChecked(memorySwitch, setting.isShowMemory());
        setChecked(gyroSwitch, setting.isEnableGyroscope());
        setChecked(hideSwitch, menu.isHideAllViews());
        scrim.setVisibility(View.VISIBLE);
    }

    private void closePanel() {
        scrim.setVisibility(View.GONE);
    }

    private void runAndClose(Runnable action) {
        closePanel();
        action.run();
    }

    private void setChecked(Switch toggle, boolean value) {
        syncing = true;
        toggle.setChecked(value);
        syncing = false;
    }

    // ---------------------------------------------------------------- construcción de la UI

    private View buildFloatingButton() {
        TextView sm = new TextView(ctx());
        sm.setText("SM");
        sm.setTextColor(Color.WHITE);
        sm.setTextSize(16);
        sm.setTypeface(Typeface.DEFAULT_BOLD);
        sm.setGravity(Gravity.CENTER);
        sm.setAlpha(0.9f);
        sm.setBackground(rounded(ACCENT_COLOR, 18));
        sm.setContentDescription("SmLauncher Client");
        sm.setOnClickListener(v -> togglePanel());
        return sm;
    }

    private RelativeLayout.LayoutParams floatingButtonParams() {
        int side = dp(44);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(side, side);
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        lp.addRule(RelativeLayout.ALIGN_PARENT_END);
        lp.topMargin = dp(12);
        lp.setMarginEnd(dp(12));
        return lp;
    }

    private FrameLayout buildScrim() {
        FrameLayout layer = new FrameLayout(ctx());
        layer.setBackgroundColor(SCRIM_COLOR);
        layer.setClickable(true);
        layer.setVisibility(View.GONE);
        layer.setOnClickListener(v -> closePanel());

        int width = Math.min(dp(340), ctx().getResources().getDisplayMetrics().widthPixels - dp(32));
        layer.addView(buildPanel(), new FrameLayout.LayoutParams(
                width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return layer;
    }

    private View buildPanel() {
        // ScrollView con altura máxima del 88 % de la pantalla: en horizontal el panel no cabe entero
        ScrollView scroll = new ScrollView(ctx()) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int max = Math.round(getResources().getDisplayMetrics().heightPixels * 0.88f);
                super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST));
            }
        };
        scroll.setBackground(rounded(PANEL_COLOR, 24));
        scroll.setClickable(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout content = new LinearLayout(ctx());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));
        scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(ctx());
        title.setText("SmLauncher Client");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        content.addView(title, rowParams(12));

        MenuSetting setting = menu.getMenuSetting();
        fpsSwitch = addSwitchRow(content, "FPS en pantalla", setting.isShowFps(), menu::smToggleFps);
        memorySwitch = addSwitchRow(content, "Memoria RAM", setting.isShowMemory(), menu::smToggleMemory);
        gyroSwitch = addSwitchRow(content, "Giroscopio", setting.isEnableGyroscope(), menu::smToggleGyroscope);
        hideSwitch = addSwitchRow(content, "Ocultar controles", menu.isHideAllViews(), menu::setHideAllViews);

        content.addView(pair(
                button("Hitboxes (F3+B)", CARD_COLOR,
                        () -> runAndClose(() -> combo(FCLKeycodes.KEY_F3, FCLKeycodes.KEY_B))),
                button("Cámara (F5)", CARD_COLOR,
                        () -> runAndClose(() -> tap(FCLKeycodes.KEY_F5)))), rowParams(8));
        content.addView(pair(
                button("Ocultar HUD (F1)", CARD_COLOR,
                        () -> runAndClose(() -> tap(FCLKeycodes.KEY_F1))),
                button("Menú (ESC)", CARD_COLOR,
                        () -> runAndClose(() -> tap(FCLKeycodes.KEY_ESC)))), rowParams(8));

        content.addView(button("Teclado rápido", CARD_COLOR,
                () -> runAndClose(menu::openQuickInput)), fullButtonParams(8));
        content.addView(button("Cerrar panel", ACCENT_COLOR, this::closePanel), fullButtonParams(0));
        return scroll;
    }

    private Switch addSwitchRow(LinearLayout parent, String text, boolean initial, BoolAction action) {
        LinearLayout row = new LinearLayout(ctx());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setBackground(rounded(CARD_COLOR, 12));

        TextView label = new TextView(ctx());
        label.setText(text);
        label.setTextColor(Color.WHITE);
        label.setTextSize(15);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(ctx());
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (!syncing) {
                action.run(checked);
            }
        });
        row.addView(toggle);

        parent.addView(row, rowParams(6));
        return toggle;
    }

    private TextView button(String text, int color, Runnable action) {
        TextView view = new TextView(ctx());
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setMaxLines(1);
        view.setClickable(true);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(RIPPLE_COLOR), rounded(color, 14), null));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private View pair(View left, View right) {
        LinearLayout row = new LinearLayout(ctx());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        leftParams.setMarginEnd(dp(4));
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        rightParams.setMarginStart(dp(4));
        row.addView(left, leftParams);
        row.addView(right, rightParams);
        return row;
    }

    private LinearLayout.LayoutParams rowParams(int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(bottomDp);
        return lp;
    }

    private LinearLayout.LayoutParams fullButtonParams(int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lp.bottomMargin = dp(bottomDp);
        return lp;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    // ---------------------------------------------------------------- teclas

    /** Pulsa y suelta una tecla (en un hilo aparte para no bloquear la UI). */
    private void tap(int key) {
        Schedulers.io().execute(() -> {
            menu.getInput().sendKeyEvent(key, true);
            pause(40);
            menu.getInput().sendKeyEvent(key, false);
        });
    }

    /** Mantiene la primera tecla, pulsa la segunda y suelta ambas (por ejemplo F3+B). */
    private void combo(int first, int second) {
        Schedulers.io().execute(() -> {
            menu.getInput().sendKeyEvent(first, true);
            pause(40);
            menu.getInput().sendKeyEvent(second, true);
            pause(40);
            menu.getInput().sendKeyEvent(second, false);
            pause(40);
            menu.getInput().sendKeyEvent(first, false);
        });
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------- utilidades

    private Context ctx() {
        return menu.getActivity();
    }

    private int dp(int value) {
        return Math.round(value * ctx().getResources().getDisplayMetrics().density);
    }
}
