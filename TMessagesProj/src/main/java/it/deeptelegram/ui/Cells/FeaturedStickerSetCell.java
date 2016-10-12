/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package it.deeptelegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import it.deeptelegram.messenger.AndroidUtilities;
import it.deeptelegram.messenger.AnimatorListenerAdapterProxy;
import it.deeptelegram.messenger.LocaleController;
import it.deeptelegram.messenger.R;
import it.deeptelegram.messenger.query.StickersQuery;
import it.deeptelegram.tgnet.TLRPC;
import it.deeptelegram.ui.Components.BackupImageView;
import it.deeptelegram.ui.Components.LayoutHelper;
import it.deeptelegram.ui.Components.Switch;

public class FeaturedStickerSetCell extends FrameLayout {

    private TextView textView;
    private TextView valueTextView;
    private BackupImageView imageView;
    private TextView addButton;
    private ImageView checkImage;
    private boolean needDivider;
    private Switch checkBox;
    private TLRPC.StickerSetCovered stickersSet;
    private Rect rect = new Rect();
    private AnimatorSet currentAnimation;
    private boolean wasLayout;

    private boolean isInstalled;

    private boolean drawProgress;
    private float progressAlpha;
    private RectF progressRect = new RectF();
    private long lastUpdateTime;
    private static Paint botProgressPaint;
    private int angle;

    private static Paint paint;

    public FeaturedStickerSetCell(Context context) {
        super(context);

        if (paint == null) {
            paint = new Paint();
            paint.setColor(0xffd9d9d9);
        }
        if (botProgressPaint == null) {
            botProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            botProgressPaint.setColor(0xffffffff);
            botProgressPaint.setStrokeCap(Paint.Cap.ROUND);
            botProgressPaint.setStyle(Paint.Style.STROKE);
        }
        botProgressPaint.setStrokeWidth(AndroidUtilities.dp(2));

        textView = new TextView(context);
        textView.setTextColor(0xff212121);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, LocaleController.isRTL ? 100 : 71, 10, LocaleController.isRTL ? 71 : 100, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(0xff8a8a8a);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, LocaleController.isRTL ? 100 : 71, 35, LocaleController.isRTL ? 71 : 100, 0));

        imageView = new BackupImageView(context);
        imageView.setAspectFit(true);
        addView(imageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 12, 8, LocaleController.isRTL ? 12 : 0, 0));

        addButton = new TextView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (drawProgress || !drawProgress && progressAlpha != 0) {
                    botProgressPaint.setAlpha(Math.min(255, (int) (progressAlpha * 255)));
                    int x = getMeasuredWidth() - AndroidUtilities.dp(11);
                    progressRect.set(x, AndroidUtilities.dp(3), x + AndroidUtilities.dp(8), AndroidUtilities.dp(8 + 3));
                    canvas.drawArc(progressRect, angle, 220, false, botProgressPaint);
                    invalidate((int) progressRect.left - AndroidUtilities.dp(2), (int) progressRect.top - AndroidUtilities.dp(2), (int) progressRect.right + AndroidUtilities.dp(2), (int) progressRect.bottom + AndroidUtilities.dp(2));
                    long newTime = System.currentTimeMillis();
                    if (Math.abs(lastUpdateTime - System.currentTimeMillis()) < 1000) {
                        long delta = (newTime - lastUpdateTime);
                        float dt = 360 * delta / 2000.0f;
                        angle += dt;
                        angle -= 360 * (angle / 360);
                        if (drawProgress) {
                            if (progressAlpha < 1.0f) {
                                progressAlpha += delta / 200.0f;
                                if (progressAlpha > 1.0f) {
                                    progressAlpha = 1.0f;
                                }
                            }
                        } else {
                            if (progressAlpha > 0.0f) {
                                progressAlpha -= delta / 200.0f;
                                if (progressAlpha < 0.0f) {
                                    progressAlpha = 0.0f;
                                }
                            }
                        }
                    }
                    lastUpdateTime = newTime;
                    invalidate();
                }
            }
        };
        addButton.setPadding(AndroidUtilities.dp(17), 0, AndroidUtilities.dp(17), 0);
        addButton.setGravity(Gravity.CENTER);
        addButton.setTextColor(0xffffffff);
        addButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        addButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addButton.setBackgroundResource(R.drawable.add_states);
        addButton.setText(LocaleController.getString("Add", R.string.Add).toUpperCase());
        addView(addButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), LocaleController.isRTL ? 14 : 0, 18, LocaleController.isRTL ? 0 : 14, 0));

        checkImage = new ImageView(context);
        checkImage.setImageResource(R.drawable.sticker_added);
        addView(checkImage, LayoutHelper.createFrame(19, 14));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int l = addButton.getLeft() + addButton.getMeasuredWidth() / 2 - checkImage.getMeasuredWidth() / 2;
        int t = addButton.getTop() + addButton.getMeasuredHeight() / 2 - checkImage.getMeasuredHeight() / 2;
        checkImage.layout(l, t, l + checkImage.getMeasuredWidth(), t + checkImage.getMeasuredHeight());
        wasLayout = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        wasLayout = false;
    }

    public void setStickersSet(TLRPC.StickerSetCovered set, boolean divider, boolean unread) {
        boolean sameSet = set == stickersSet && wasLayout;
        needDivider = divider;
        stickersSet = set;
        lastUpdateTime = System.currentTimeMillis();
        setWillNotDraw(!needDivider);
        if (currentAnimation != null) {
            currentAnimation.cancel();
            currentAnimation = null;
        }

        textView.setText(stickersSet.set.title);
        if (unread) {
            Drawable drawable = new Drawable() {

                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

                @Override
                public void draw(Canvas canvas) {
                    paint.setColor(0xff44a8ea);
                    canvas.drawCircle(AndroidUtilities.dp(4), AndroidUtilities.dp(5), AndroidUtilities.dp(3), paint);
                }

                @Override
                public void setAlpha(int alpha) {

                }

                @Override
                public void setColorFilter(ColorFilter colorFilter) {

                }

                @Override
                public int getOpacity() {
                    return 0;
                }

                @Override
                public int getIntrinsicWidth() {
                    return AndroidUtilities.dp(12);
                }

                @Override
                public int getIntrinsicHeight() {
                    return AndroidUtilities.dp(8);
                }
            };
            textView.setCompoundDrawablesWithIntrinsicBounds(LocaleController.isRTL ? null : drawable, null, LocaleController.isRTL ? drawable : null, null);
        } else {
            textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }

        valueTextView.setText(LocaleController.formatPluralString("Stickers", set.set.count));
        if (set.cover != null && set.cover.thumb != null && set.cover.thumb.location != null) {
            imageView.setImage(set.cover.thumb.location, null, "webp", null);
        } else if (!set.covers.isEmpty()) {
            imageView.setImage(set.covers.get(0).thumb.location, null, "webp", null);
        }

        if (sameSet) {
            boolean wasInstalled = isInstalled;
            if (isInstalled = StickersQuery.isStickerPackInstalled(set.set.id)) {
                if (!wasInstalled) {
                    checkImage.setVisibility(VISIBLE);
                    addButton.setClickable(false);
                    currentAnimation = new AnimatorSet();
                    currentAnimation.setDuration(200);
                    currentAnimation.playTogether(ObjectAnimator.ofFloat(addButton, "alpha", 1.0f, 0.0f),
                            ObjectAnimator.ofFloat(addButton, "scaleX", 1.0f, 0.01f),
                            ObjectAnimator.ofFloat(addButton, "scaleY", 1.0f, 0.01f),
                            ObjectAnimator.ofFloat(checkImage, "alpha", 0.0f, 1.0f),
                            ObjectAnimator.ofFloat(checkImage, "scaleX", 0.01f, 1.0f),
                            ObjectAnimator.ofFloat(checkImage, "scaleY", 0.01f, 1.0f));
                    currentAnimation.addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationEnd(Animator animator) {
                            if (currentAnimation != null && currentAnimation.equals(animator)) {
                                addButton.setVisibility(INVISIBLE);
                            }
                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {
                            if (currentAnimation != null && currentAnimation.equals(animator)) {
                                currentAnimation = null;
                            }
                        }
                    });
                    currentAnimation.start();
                }
            } else {
                if (wasInstalled) {
                    addButton.setVisibility(VISIBLE);
                    addButton.setClickable(true);
                    currentAnimation = new AnimatorSet();
                    currentAnimation.setDuration(200);
                    currentAnimation.playTogether(ObjectAnimator.ofFloat(checkImage, "alpha", 1.0f, 0.0f),
                            ObjectAnimator.ofFloat(checkImage, "scaleX", 1.0f, 0.01f),
                            ObjectAnimator.ofFloat(checkImage, "scaleY", 1.0f, 0.01f),
                            ObjectAnimator.ofFloat(addButton, "alpha", 0.0f, 1.0f),
                            ObjectAnimator.ofFloat(addButton, "scaleX", 0.01f, 1.0f),
                            ObjectAnimator.ofFloat(addButton, "scaleY", 0.01f, 1.0f));
                    currentAnimation.addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationEnd(Animator animator) {
                            if (currentAnimation != null && currentAnimation.equals(animator)) {
                                checkImage.setVisibility(INVISIBLE);
                            }
                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {
                            if (currentAnimation != null && currentAnimation.equals(animator)) {
                                currentAnimation = null;
                            }
                        }
                    });
                    currentAnimation.start();
                }
            }
        } else {
            if (isInstalled = StickersQuery.isStickerPackInstalled(set.set.id)) {
                addButton.setVisibility(INVISIBLE);
                addButton.setClickable(false);
                checkImage.setVisibility(VISIBLE);
                checkImage.setScaleX(1.0f);
                checkImage.setScaleY(1.0f);
                checkImage.setAlpha(1.0f);
            } else {
                addButton.setVisibility(VISIBLE);
                addButton.setClickable(true);
                checkImage.setVisibility(INVISIBLE);
                addButton.setScaleX(1.0f);
                addButton.setScaleY(1.0f);
                addButton.setAlpha(1.0f);
            }
        }
    }

    public TLRPC.StickerSetCovered getStickerSet() {
        return stickersSet;
    }

    public void setAddOnClickListener(OnClickListener onClickListener) {
        addButton.setOnClickListener(onClickListener);
    }

    public void setDrawProgress(boolean value) {
        drawProgress = value;
        lastUpdateTime = System.currentTimeMillis();
        addButton.invalidate();
    }

    public boolean isInstalled() {
        return isInstalled;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (Build.VERSION.SDK_INT >= 21 && getBackground() != null) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                getBackground().setHotspot(event.getX(), event.getY());
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(0, getHeight() - 1, getWidth() - getPaddingRight(), getHeight() - 1, paint);
        }
    }
}
