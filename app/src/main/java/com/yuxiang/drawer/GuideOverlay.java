package com.yuxiang.drawer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

public class GuideOverlay extends FrameLayout {
    private static final Interpolator INTERPOLATOR = new AccelerateDecelerateInterpolator();
    private static final long REVEAL_DURATION = 225L;
    private static final long IDLE_START_DELAY = 225L;
    private static final long BREATH_DURATION = 1000L;
    private static final float BREATH_SCALE = 1.1f;
    private static final long RIPPLE_DURATION = 500L;
    private static final float RIPPLE_SCALE = 1.6f;
    private static final long FADE_DURATION = 225L;
    private static final int SCRIM_ALPHA = 220;
    private static final int FOCAL_ALPHA = 76;
    private static final int RIPPLE_ALPHA = 64;

    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path scrimPath = new Path();
    private final Path ringPath = new Path();
    private final RectF targetRect = new RectF();
    private final RectF targetBounds = new RectF();
    private final RectF focalBounds = new RectF();
    private final RectF holeBounds = new RectF();
    private final RectF shapeBounds = new RectF();

    private final View textGroup;
    private final TextView titleView;
    private final TextView descriptionView;
    private final float focalPadding;
    private final float textPadding;
    private final float textSpacing;
    private final float maxTextWidth;

    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener =
            new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (prepare()) {
                        requestLayout();
                    }
                    invalidate();
                }
            };

    private View target;
    private float centreX;
    private float centreY;
    private float scrimRadius;
    private float holeRadius;
    private float focalRadius;
    private float reveal;
    private float alpha;
    private int viewWidth;
    private int viewHeight;
    private float breath = 1f;
    private float ripple;
    private float rippleAlpha;
    private boolean rising = true;
    private boolean focalPressed;
    private boolean ending;
    private boolean started;

    private ValueAnimator revealAnimator;
    private ValueAnimator breathAnimator;
    private ValueAnimator rippleAnimator;
    private ValueAnimator fadeAnimator;

    public GuideOverlay(Activity activity) {
        super(activity);
        setWillNotDraw(false);
        LayoutInflater.from(activity).inflate(R.layout.view_guide_overlay, this, true);

        textGroup = findViewById(R.id.guide_text);
        titleView = findViewById(R.id.guide_title);
        descriptionView = findViewById(R.id.guide_description);
        scrimPaint.setColor(ContextCompat.getColor(activity, R.color.md_theme_inverseSurface));
        focalPaint.setColor(ContextCompat.getColor(activity, R.color.md_theme_inversePrimary));
        ripplePaint.setColor(ContextCompat.getColor(activity, R.color.md_theme_inversePrimary));

        focalPadding = getResources().getDimension(R.dimen.spacing_m);
        textPadding = getResources().getDimension(R.dimen.spacing_xl);
        textSpacing = getResources().getDimension(R.dimen.spacing_l);
        maxTextWidth = getResources().getDimension(R.dimen.tooltip_max_width);
    }

    public void show(View target, CharSequence title, CharSequence description) {
        if (this.target != null || ending || target == null) {
            return;
        }
        ViewGroup parent = ((Activity) getContext()).findViewById(android.R.id.content);
        if (parent == null) {
            return;
        }
        this.target = target;
        titleView.setText(title);
        descriptionView.setText(description);
        textGroup.setAlpha(0f);
        parent.addView(this, new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
    }

    public boolean isShowing() {
        return target != null && !ending;
    }

    public void finish() {
        endGuide(true);
    }

    public void dismiss() {
        endGuide(false);
    }

    private void endGuide(final boolean expand) {
        if (target == null || ending) {
            return;
        }
        ending = true;
        cancelAnimations();
        fadeAnimator = ValueAnimator.ofFloat(1f, 0f);
        fadeAnimator.setDuration(FADE_DURATION);
        fadeAnimator.setInterpolator(INTERPOLATOR);
        fadeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float value = (float) animation.getAnimatedValue();
                updateAnimation(expand ? 1f + ((1f - value) / 4f) : value, value);
            }
        });
        fadeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeOverlay();
            }
        });
        fadeAnimator.start();
    }

    private void startRevealAnimation() {
        cancelAnimations();
        revealAnimator = ValueAnimator.ofFloat(0f, 1f);
        revealAnimator.setDuration(REVEAL_DURATION);
        revealAnimator.setInterpolator(INTERPOLATOR);
        revealAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float value = (float) animation.getAnimatedValue();
                updateAnimation(value, value);
            }
        });
        revealAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                revealAnimator = null;
                updateAnimation(1f, 1f);
                startIdleAnimations();
            }
        });
        revealAnimator.start();
    }

    private void startIdleAnimations() {
        breathAnimator = ValueAnimator.ofFloat(1f, BREATH_SCALE, 1f);
        breathAnimator.setDuration(BREATH_DURATION);
        breathAnimator.setStartDelay(IDLE_START_DELAY);
        breathAnimator.setRepeatCount(ValueAnimator.INFINITE);
        breathAnimator.setInterpolator(INTERPOLATOR);
        breathAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float value = (float) animation.getAnimatedValue();
                if (value < breath && rising) {
                    rising = false;
                    startRippleAnimation();
                } else if (value > breath) {
                    rising = true;
                }
                breath = value;
                invalidate();
            }
        });
        breathAnimator.start();
    }

    private void startRippleAnimation() {
        if (rippleAnimator != null) {
            rippleAnimator.removeAllUpdateListeners();
            rippleAnimator.removeAllListeners();
            rippleAnimator.cancel();
        }
        rippleAnimator = ValueAnimator.ofFloat(BREATH_SCALE, RIPPLE_SCALE);
        rippleAnimator.setDuration(RIPPLE_DURATION);
        rippleAnimator.setInterpolator(INTERPOLATOR);
        rippleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                ripple = (float) animation.getAnimatedValue();
                rippleAlpha = (RIPPLE_SCALE - ripple) / (RIPPLE_SCALE - BREATH_SCALE);
                invalidate();
            }
        });
        rippleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                rippleAnimator = null;
                ripple = 0f;
                rippleAlpha = 0f;
                invalidate();
            }
        });
        rippleAnimator.start();
    }

    private void updateAnimation(float revealValue, float alphaValue) {
        reveal = revealValue;
        alpha = alphaValue;
        textGroup.setAlpha(alphaValue);
        invalidate();
    }

    private void cancelAnimations() {
        revealAnimator = cancel(revealAnimator);
        breathAnimator = cancel(breathAnimator);
        rippleAnimator = cancel(rippleAnimator);
        fadeAnimator = cancel(fadeAnimator);
    }

    private ValueAnimator cancel(ValueAnimator animator) {
        if (animator != null) {
            animator.removeAllUpdateListeners();
            animator.removeAllListeners();
            animator.cancel();
        }
        return null;
    }

    private void removeOverlay() {
        cancelAnimations();
        final ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) {
            parent.removeView(this);
        }
    }

    private boolean prepare() {
        if (target == null || getWidth() == 0 || getHeight() == 0) {
            return false;
        }
        final int[] targetPosition = new int[2];
        target.getLocationInWindow(targetPosition);
        final int[] viewPosition = new int[2];
        getLocationInWindow(viewPosition);
        final float left = targetPosition[0] - viewPosition[0];
        final float top = targetPosition[1] - viewPosition[1];
        final int width = target.getWidth();
        final int height = target.getHeight();
        if (left == targetRect.left && top == targetRect.top
                && width == targetRect.width() && height == targetRect.height()
                && getWidth() == viewWidth && getHeight() == viewHeight) {
            return false;
        }
        viewWidth = getWidth();
        viewHeight = getHeight();
        targetRect.set(left, top, left + width, top + height);
        targetBounds.set(targetRect);
        targetBounds.intersect(0f, 0f, viewWidth, viewHeight);
        focalBounds.set(targetBounds);
        focalBounds.inset(-focalPadding, -focalPadding);
        centreX = targetBounds.centerX();
        centreY = targetBounds.centerY();
        holeRadius = Math.min(targetBounds.width(), targetBounds.height()) / 2f;
        focalRadius = Math.min(focalBounds.width(), focalBounds.height()) / 2f;
        scrimRadius = (float) Math.hypot(Math.max(centreX, viewWidth - centreX),
                Math.max(centreY, viewHeight - centreY)) + 1f;
        return true;
    }

    private void scaleBounds(RectF out, RectF base, float scale) {
        final float halfWidth = base.width() / 2f * scale;
        final float halfHeight = base.height() / 2f * scale;
        out.set(centreX - halfWidth, centreY - halfHeight, centreX + halfWidth, centreY + halfHeight);
    }

    private boolean isInsideFocal(float x, float y) {
        return focalBounds.contains(x, y);
    }

    private void clickTarget() {
        if (target != null && target.isEnabled() && target.isClickable()) {
            target.performClick();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAnimations();
        final ViewTreeObserver observer = getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(layoutListener);
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (!started && target != null && width > 0 && height > 0
                && target.getWidth() > 0 && target.getHeight() > 0) {
            started = true;
            prepare();
            startRevealAnimation();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int available = Math.max(0, (int) Math.min(maxTextWidth, width - (2f * textPadding)));
        textGroup.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        prepare();
        final int groupWidth = textGroup.getMeasuredWidth();
        final int groupHeight = textGroup.getMeasuredHeight();
        float groupLeft = centreX - (groupWidth / 2f);
        groupLeft = Math.max(textPadding, Math.min(groupLeft, getWidth() - textPadding - groupWidth));
        float groupTop = centreY > getHeight() / 2f
                ? focalBounds.top - textSpacing - groupHeight
                : focalBounds.bottom + textSpacing;
        groupTop = Math.max(textPadding, Math.min(groupTop, getHeight() - textPadding - groupHeight));
        textGroup.layout((int) groupLeft, (int) groupTop,
                (int) groupLeft + groupWidth, (int) groupTop + groupHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (target == null) {
            return;
        }
        if (targetBounds.isEmpty()) {
            scrimPaint.setAlpha((int) (SCRIM_ALPHA * alpha));
            canvas.drawRect(0f, 0f, getWidth(), getHeight(), scrimPaint);
            return;
        }

        final float shape = reveal * breath;

        scrimPath.reset();
        scrimPath.setFillType(Path.FillType.EVEN_ODD);
        scrimPath.addCircle(centreX, centreY, scrimRadius * reveal, Path.Direction.CW);
        scaleBounds(holeBounds, targetBounds, shape);
        scrimPath.addRoundRect(holeBounds, holeRadius * shape, holeRadius * shape, Path.Direction.CW);
        scrimPaint.setAlpha((int) (SCRIM_ALPHA * alpha));
        canvas.drawPath(scrimPath, scrimPaint);

        scaleBounds(shapeBounds, focalBounds, shape);
        ringPath.reset();
        ringPath.setFillType(Path.FillType.EVEN_ODD);
        ringPath.addRoundRect(shapeBounds, focalRadius * shape, focalRadius * shape, Path.Direction.CW);
        ringPath.addRoundRect(holeBounds, holeRadius * shape, holeRadius * shape, Path.Direction.CW);
        focalPaint.setAlpha((int) (FOCAL_ALPHA * alpha));
        canvas.drawPath(ringPath, focalPaint);

        if (ripple > 0f) {
            final float rippleShape = reveal * ripple;
            scaleBounds(shapeBounds, focalBounds, rippleShape);
            ringPath.reset();
            ringPath.setFillType(Path.FillType.EVEN_ODD);
            ringPath.addRoundRect(shapeBounds, focalRadius * rippleShape, focalRadius * rippleShape, Path.Direction.CW);
            ringPath.addRoundRect(holeBounds, holeRadius * shape, holeRadius * shape, Path.Direction.CW);
            ripplePaint.setAlpha((int) (RIPPLE_ALPHA * rippleAlpha * alpha));
            canvas.drawPath(ringPath, ripplePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                focalPressed = isInsideFocal(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
                if (focalPressed && isInsideFocal(event.getX(), event.getY())) {
                    finish();
                    clickTarget();
                } else if (!focalPressed) {
                    dismiss();
                }
                return true;
            default:
                return true;
        }
    }
}