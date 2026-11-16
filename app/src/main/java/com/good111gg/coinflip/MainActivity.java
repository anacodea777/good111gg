package com.good111gg.coinflip;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.Locale;

public class MainActivity extends Activity implements CoinView.Listener {

    private static final String PREFS = "settings";
    private static final String KEY_LANG = "lang";
    private static final String KEY_SOUND = "sound";
    private static final String KEY_HEADS = "heads";
    private static final String KEY_TAILS = "tails";
    private static final String KEY_LAST_HEADS = "last_heads";

    private SharedPreferences prefs;
    private CoinView coin;
    private TextView result;
    private TextView hint;
    private TextView countHeads;
    private TextView countTails;
    private TextView countTotal;
    private ImageButton btnSound;
    private CoinSound sound;
    private NumberFormat numberFormat;
    private int heads;
    private int tails;

    // ------------------------------------------------------------ language

    private static String savedLanguage(Context ctx) {
        String lang = ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LANG, null);
        if (lang == null) {
            lang = "bn".equals(Locale.getDefault().getLanguage()) ? "bn" : "en";
        }
        return lang;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        Locale locale = new Locale(savedLanguage(newBase));
        Locale.setDefault(locale);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.setLocale(locale);
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableEdgeToEdge();
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        numberFormat = NumberFormat.getIntegerInstance(new Locale(savedLanguage(this)));

        View root = findViewById(R.id.root);
        applyInsets(root);

        TextView title = findViewById(R.id.title);
        SpannableString brand = new SpannableString("good111gg");
        brand.setSpan(new ForegroundColorSpan(getColor(R.color.gold)), 7, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(brand);

        coin = findViewById(R.id.coin);
        result = findViewById(R.id.result);
        hint = findViewById(R.id.hint);
        countHeads = findViewById(R.id.countHeads);
        countTails = findViewById(R.id.countTails);
        countTotal = findViewById(R.id.countTotal);
        btnSound = findViewById(R.id.btnSound);

        heads = prefs.getInt(KEY_HEADS, 0);
        tails = prefs.getInt(KEY_TAILS, 0);
        coin.setHeadsUp(prefs.getBoolean(KEY_LAST_HEADS, true));
        coin.setListener(this);

        sound = new CoinSound();
        updateSoundButton(prefs.getBoolean(KEY_SOUND, true));
        btnSound.setOnClickListener(v -> {
            boolean on = !prefs.getBoolean(KEY_SOUND, true);
            prefs.edit().putBoolean(KEY_SOUND, on).apply();
            updateSoundButton(on);
        });

        findViewById(R.id.btnLanguage).setOnClickListener(v -> {
            if (coin.isFlipping()) return;
            String next = "bn".equals(savedLanguage(this)) ? "en" : "bn";
            prefs.edit().putString(KEY_LANG, next).apply();
            recreate();
        });

        findViewById(R.id.btnReset).setOnClickListener(v -> {
            if (coin.isFlipping()) return;
            heads = 0;
            tails = 0;
            saveCounts();
            updateCounts();
            result.setText("");
            hint.setText(R.string.tap_to_flip);
        });

        updateCounts();
    }

    @Override
    protected void onDestroy() {
        if (sound != null) sound.release();
        super.onDestroy();
    }

    // ------------------------------------------------------------ coin callbacks

    @Override
    public void onFlipStart() {
        result.animate().cancel();
        result.setText("");
        hint.setText(R.string.flipping);
        sound.playFlick();
    }

    @Override
    public void onImpact(boolean firstImpact) {
        sound.playPing(firstImpact);
        if (firstImpact) {
            coin.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    @Override
    public void onFlipEnd(boolean isHeads) {
        if (isHeads) heads++; else tails++;
        prefs.edit().putBoolean(KEY_LAST_HEADS, isHeads).apply();
        saveCounts();
        updateCounts();

        String label = getString(isHeads ? R.string.heads : R.string.tails);
        result.setTextColor(getColor(isHeads ? R.color.gold : R.color.mint));
        result.setText(label);
        result.setScaleX(0.5f);
        result.setScaleY(0.5f);
        result.setAlpha(0f);
        result.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(380)
                .setInterpolator(new OvershootInterpolator(2.2f)).start();
        hint.setText(R.string.tap_to_flip);
        coin.announceForAccessibility(getString(R.string.result_announcement, label));
    }

    // ------------------------------------------------------------ helpers

    private void updateSoundButton(boolean on) {
        sound.setEnabled(on);
        btnSound.setImageResource(on ? R.drawable.ic_volume_on : R.drawable.ic_volume_off);
        btnSound.setContentDescription(getString(on ? R.string.sound_on : R.string.sound_off));
    }

    private void saveCounts() {
        prefs.edit().putInt(KEY_HEADS, heads).putInt(KEY_TAILS, tails).apply();
    }

    private void updateCounts() {
        countHeads.setText(numberFormat.format(heads));
        countTails.setText(numberFormat.format(tails));
        countTotal.setText(numberFormat.format(heads + tails));
    }

    @SuppressWarnings("deprecation")
    private void enableEdgeToEdge() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @SuppressWarnings("deprecation")
    private static void applyInsets(View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
    }
}
