package com.good111gg.coinflip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.security.SecureRandom;

/**
 * Draws a gold coin and animates a physical-looking toss: the coin spins around its
 * horizontal axis while rising and falling on a parabola, lands, bounces once and settles.
 * The outcome is picked with SecureRandom before the toss starts (fair 50/50).
 */
public class CoinView extends View {

    public interface Listener {
        void onFlipStart();
        /** Called on each impact with the ground; {@code firstImpact} is the louder one. */
        void onImpact(boolean firstImpact);
        void onFlipEnd(boolean heads);
    }

    private static final long DURATION_MS = 2300;
    private static final float AIR_PHASE = 0.76f;      // share of the animation spent in the air
    private static final float BOUNCE_LIFT = 0.07f;    // bounce height relative to the toss height
    private static final float BOUNCE_PHASE = 0.5f;    // share of the landing phase used by the bounce

    private final SecureRandom random = new SecureRandom();
    private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bevelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint embossLight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint embossDark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint embossFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgeLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path crownPath = new Path();
    private final Matrix shineMatrix = new Matrix();
    private final RectF tmpRect = new RectF();

    private float radius;
    private float restY;
    private float maxLift;
    private float angle;          // degrees; 0 = heads up, 180 = tails up
    private float lift;           // 0..1 of maxLift
    private boolean flipping;
    private boolean pendingHeads;
    private ValueAnimator animator;
    private Listener listener;

    public CoinView(Context context) {
        this(context, null);
    }

    public CoinView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setFocusable(true);

        rimPaint.setStyle(Paint.Style.STROKE);
        bevelPaint.setStyle(Paint.Style.STROKE);
        edgeLinePaint.setStyle(Paint.Style.STROKE);
        edgeLinePaint.setColor(0x55704A00);
        dotPaint.setColor(0xFFFFE680);

        Typeface tf = Typeface.create("sans-serif-black", Typeface.BOLD);
        for (Paint p : new Paint[]{embossLight, embossDark, embossFill}) {
            p.setTypeface(tf);
            p.setTextAlign(Paint.Align.CENTER);
        }
        embossLight.setColor(0xCCFFF6C2);
        embossDark.setColor(0xAA6E4A00);
        embossFill.setColor(0xFFE0A400);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setHeadsUp(boolean heads) {
        if (!flipping) {
            angle = heads ? 0 : 180;
            invalidate();
        }
    }

    public boolean isFlipping() {
        return flipping;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        radius = Math.min(w * 0.34f, h * 0.22f);
        restY = h * 0.68f;
        maxLift = Math.max(0, restY - radius * 1.32f);
        buildShaders();
    }

    private void buildShaders() {
        float r = radius;
        if (r <= 0) return;
        facePaint.setShader(new RadialGradient(-0.35f * r, -0.4f * r, 1.55f * r,
                new int[]{0xFFFFF6C4, 0xFFFFD31A, 0xFFF0B400, 0xFFB98200},
                new float[]{0f, 0.35f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        rimPaint.setStrokeWidth(r * 0.075f);
        rimPaint.setShader(new LinearGradient(-r, -r, r, r,
                new int[]{0xFFFFEC8A, 0xFFD4A000, 0xFF9C6B00},
                null, Shader.TileMode.CLAMP));
        bevelPaint.setStrokeWidth(r * 0.022f);
        bevelPaint.setShader(new LinearGradient(-r, -r, r, r,
                new int[]{0xFF9C6B00, 0xFFFFF0A0},
                null, Shader.TileMode.CLAMP));
        edgePaint.setShader(new LinearGradient(-r, 0, r, 0,
                new int[]{0xFF7A5200, 0xFFE8B800, 0xFFFFE27A, 0xFFC99500, 0xFF6E4A00},
                new float[]{0f, 0.3f, 0.5f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        edgeLinePaint.setStrokeWidth(Math.max(1f, r * 0.012f));
        shinePaint.setShader(new LinearGradient(-r, -r, r, r,
                new int[]{0x00FFFFFF, 0x00FFFFFF, 0x88FFFFFF, 0x00FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.4f, 0.5f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        shadowPaint.setShader(new RadialGradient(0, 0, 1f,
                new int[]{0x99000000, 0x44000000, 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        float ts = r * 0.62f;
        embossLight.setTextSize(ts);
        embossDark.setTextSize(ts);
        embossFill.setTextSize(ts);

        // Crown emblem for the heads face (built at radius r, centred on 0,0).
        crownPath.reset();
        crownPath.moveTo(-0.50f * r, 0.16f * r);
        crownPath.lineTo(-0.58f * r, -0.30f * r);
        crownPath.lineTo(-0.27f * r, -0.04f * r);
        crownPath.lineTo(0f, -0.44f * r);
        crownPath.lineTo(0.27f * r, -0.04f * r);
        crownPath.lineTo(0.58f * r, -0.30f * r);
        crownPath.lineTo(0.50f * r, 0.16f * r);
        crownPath.close();
        crownPath.addRoundRect(new RectF(-0.52f * r, 0.22f * r, 0.52f * r, 0.36f * r),
                0.04f * r, 0.04f * r, Path.Direction.CW);
        float b = 0.07f * r;
        crownPath.addCircle(-0.58f * r, -0.30f * r, b, Path.Direction.CW);
        crownPath.addCircle(0f, -0.44f * r, b, Path.Direction.CW);
        crownPath.addCircle(0.58f * r, -0.30f * r, b, Path.Direction.CW);
    }

    // ---------------------------------------------------------------- toss

    public void flip() {
        if (flipping || radius <= 0) return;
        flipping = true;
        pendingHeads = random.nextBoolean();

        final float start = isHeadsAt(angle) ? 0f : 180f;
        int halfTurns = 15 + random.nextInt(6);
        boolean endsHeads = ((start == 0f) == (halfTurns % 2 == 0));
        if (endsHeads != pendingHeads) halfTurns++;
        final float target = start + 180f * halfTurns;

        if (listener != null) listener.onFlipStart();

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION_MS + random.nextInt(250));
        animator.setInterpolator(new LinearInterpolator());
        final boolean[] impacts = new boolean[2];
        animator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            if (t < AIR_PHASE) {
                float u = t / AIR_PHASE;
                lift = 4f * u * (1f - u);                 // ballistic arc
                angle = start + (target - start) * u;     // constant spin rate in the air
            } else {
                if (!impacts[0]) {
                    impacts[0] = true;
                    if (listener != null) listener.onImpact(true);
                }
                float v = (t - AIR_PHASE) / (1f - AIR_PHASE);
                float bv = Math.min(v / BOUNCE_PHASE, 1f);
                lift = BOUNCE_LIFT * 4f * bv * (1f - bv);
                if (bv >= 1f && !impacts[1]) {
                    impacts[1] = true;
                    if (listener != null) listener.onImpact(false);
                }
                // small settling wobble that dies out
                float wobble = (float) (Math.sin(v * Math.PI * 4) * 14f * (1f - v) * (1f - v));
                angle = target + wobble;
            }
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                angle = pendingHeads ? 0f : 180f;
                lift = 0f;
                flipping = false;
                invalidate();
                if (listener != null) listener.onFlipEnd(pendingHeads);
            }
        });
        animator.start();
    }

    private static boolean isHeadsAt(float deg) {
        return Math.cos(Math.toRadians(deg)) >= 0;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            performClick();
            return true;
        }
        return event.getAction() == MotionEvent.ACTION_DOWN || super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        flip();
        return true;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas canvas) {
        if (radius <= 0) return;
        float cx = getWidth() / 2f;
        float r = radius;
        float cy = restY - lift * maxLift;
        float scale = 1f + 0.24f * lift;      // closer to the viewer when high

        // Shadow on the ground: smaller and fainter the higher the coin is.
        float groundY = restY + r * 1.08f;
        float sw = r * (1.05f - 0.55f * lift);
        float sh = sw * 0.16f;
        canvas.save();
        canvas.translate(cx, groundY);
        canvas.scale(sw, sh);
        shadowPaint.setAlpha((int) (255 * (1f - 0.65f * lift)));
        canvas.drawCircle(0, 0, 1f, shadowPaint);
        canvas.restore();

        double rad = Math.toRadians(angle);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        float squash = Math.max(Math.abs(c), 0.004f);
        float thickness = r * 0.11f * Math.abs(s);
        float dir = (s * c >= 0) ? 1f : -1f;
        boolean headsVisible = c >= 0;

        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);

        float faceY = -dir * thickness / 2f;
        float backY = dir * thickness / 2f;

        // Edge (cylinder side) between back ellipse and front ellipse.
        if (thickness > 0.5f) {
            tmpRect.set(-r, backY - r * squash, r, backY + r * squash);
            canvas.drawOval(tmpRect, edgePaint);
            float top = Math.min(faceY, backY);
            float bottom = Math.max(faceY, backY);
            canvas.drawRect(-r, top, r, bottom, edgePaint);
            if (thickness > 3f) {
                for (int i = 1; i < 36; i++) {
                    float x = (float) (-r * Math.cos(Math.PI * i / 36));
                    float yOff = (float) (r * squash * Math.sin(Math.PI * i / 36)) * dir;
                    canvas.drawLine(x, top + yOff, x, bottom + yOff, edgeLinePaint);
                }
            }
        }

        // Face
        canvas.save();
        canvas.translate(0, faceY);
        canvas.scale(1f, squash);
        drawFace(canvas, r, headsVisible);

        // Darken as the face tilts away from the light, plus a moving glint while spinning.
        int shade = (int) ((1f - Math.abs(c)) * 120);
        if (shade > 0) {
            shadePaint.setColor(Color.argb(shade, 40, 25, 0));
            canvas.drawCircle(0, 0, r, shadePaint);
        }
        if (flipping) {
            float phase = (float) (((angle % 360) + 360) % 360) / 360f;
            shineMatrix.setTranslate((phase * 2f - 1f) * r * 1.6f, (phase * 2f - 1f) * r * 1.6f);
            shinePaint.getShader().setLocalMatrix(shineMatrix);
            canvas.drawCircle(0, 0, r, shinePaint);
        }
        canvas.restore();

        canvas.restore();
    }

    private void drawFace(Canvas canvas, float r, boolean heads) {
        canvas.drawCircle(0, 0, r, facePaint);
        canvas.drawCircle(0, 0, r * 0.96f, rimPaint);
        canvas.drawCircle(0, 0, r * 0.86f, bevelPaint);

        // ring of small dots
        float dotR = r * 0.022f;
        for (int i = 0; i < 40; i++) {
            double a = 2 * Math.PI * i / 40;
            canvas.drawCircle((float) (Math.cos(a) * r * 0.78f), (float) (Math.sin(a) * r * 0.78f), dotR, dotPaint);
        }

        float d = Math.max(1.5f, r * 0.018f);
        if (heads) {
            canvas.save();
            canvas.translate(d, d);
            canvas.drawPath(crownPath, embossDark);
            canvas.translate(-2 * d, -2 * d);
            canvas.drawPath(crownPath, embossLight);
            canvas.restore();
            canvas.drawPath(crownPath, embossFill);
        } else {
            String label = "111";
            float baseline = -(embossFill.ascent() + embossFill.descent()) / 2f;
            canvas.drawText(label, d, baseline + d, embossDark);
            canvas.drawText(label, -d, baseline - d, embossLight);
            canvas.drawText(label, 0, baseline, embossFill);
        }
    }
}
