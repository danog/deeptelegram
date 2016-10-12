/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package it.deeptelegram.ui.Components;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import it.deeptelegram.messenger.AndroidUtilities;
import it.deeptelegram.messenger.AnimatorListenerAdapterProxy;
import it.deeptelegram.messenger.ApplicationLoader;
import it.deeptelegram.messenger.MessagesController;
import it.deeptelegram.messenger.ContactsController;
import it.deeptelegram.messenger.FileLog;
import it.deeptelegram.messenger.LocaleController;
import it.deeptelegram.messenger.MediaController;
import it.deeptelegram.messenger.MessageObject;
import it.deeptelegram.messenger.NotificationCenter;
import it.deeptelegram.messenger.camera.*;
import it.deeptelegram.messenger.query.SearchQuery;
import it.deeptelegram.messenger.support.widget.LinearLayoutManager;
import it.deeptelegram.messenger.R;
import it.deeptelegram.messenger.support.widget.RecyclerView;
import it.deeptelegram.tgnet.TLRPC;
import it.deeptelegram.ui.ActionBar.BottomSheet;
import it.deeptelegram.ui.ActionBar.Theme;
import it.deeptelegram.ui.Cells.PhotoAttachCameraCell;
import it.deeptelegram.ui.Cells.PhotoAttachPhotoCell;
import it.deeptelegram.ui.Cells.ShadowSectionCell;
import it.deeptelegram.ui.ChatActivity;
import it.deeptelegram.ui.PhotoViewer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class ChatAttachAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate, PhotoViewer.PhotoViewerProvider, BottomSheet.BottomSheetDelegateInterface {

    public interface ChatAttachViewDelegate {
        void didPressedButton(int button);
        View getRevealView();
        void didSelectBot(TLRPC.User user);
    }

    private class InnerAnimator {
        private AnimatorSet animatorSet;
        private float startRadius;
    }

    private LinearLayoutManager attachPhotoLayoutManager;
    private PhotoAttachAdapter photoAttachAdapter;
    private ChatActivity baseFragment;
    private AttachButton sendPhotosButton;
    private View views[] = new View[20];
    private RecyclerListView attachPhotoRecyclerView;
    private View lineView;
    private EmptyTextProgressView progressView;
    private ArrayList<Holder> viewsCache = new ArrayList<>(8);
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private Drawable shadowDrawable;
    private ViewGroup attachView;
    private ListAdapter adapter;
    private TextView hintTextView;
    private ArrayList<InnerAnimator> innerAnimators = new ArrayList<>();

    private CameraView cameraView;
    private FrameLayout cameraIcon;
    private TextView recordTime;
    private ImageView[] flashModeButton = new ImageView[2];
    private boolean flashAnimationInProgress;
    private int[] cameraViewLocation = new int[2];
    private int cameraViewOffsetX;
    private int cameraViewOffsetY;
    private boolean cameraOpened;
    private boolean cameraInitied;
    private boolean cameraAnimationInProgress;
    private float cameraOpenProgress;
    private int[] animateCameraValues = new int[5];
    private int videoRecordTime;
    private Runnable videoRecordRunnable;
    private DecelerateInterpolator interpolator = new DecelerateInterpolator(1.5f);
    private FrameLayout cameraPanel;
    private ShutterButton shutterButton;
    private ImageView switchCameraButton;
    private File cameraFile;
    private boolean takingPhoto;
    private ArrayList<Object> cameraPhoto;

    private float lastY;
    private boolean pressed;
    private boolean maybeStartDraging;

    private AnimatorSet currentHintAnimation;
    private boolean hintShowed;
    private Runnable hideHintRunnable;

    private boolean deviceHasGoodCamera;

    private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

    private boolean loading = true;

    private ChatAttachViewDelegate delegate;

    private int scrollOffsetY;
    private boolean ignoreLayout;

    private boolean useRevealAnimation;
    private float revealRadius;
    private int revealX;
    private int revealY;
    private boolean revealAnimationInProgress;

    private class AttachButton extends FrameLayout {

        private TextView textView;
        private ImageView imageView;

        public AttachButton(Context context) {
            super(context);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrame(64, 64, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

            textView = new TextView(context);
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextColor(Theme.ATTACH_SHEET_TEXT_COLOR);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 64, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(85), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(90), MeasureSpec.EXACTLY));
        }

        public void setTextAndIcon(CharSequence text, Drawable drawable) {
            textView.setText(text);
            imageView.setBackgroundDrawable(drawable);
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    private class AttachBotButton extends FrameLayout {

        private BackupImageView imageView;
        private TextView nameTextView;
        private AvatarDrawable avatarDrawable = new AvatarDrawable();
        private boolean pressed;

        private boolean checkingForLongPress = false;
        private CheckForLongPress pendingCheckForLongPress = null;
        private int pressCount = 0;
        private CheckForTap pendingCheckForTap = null;

        private TLRPC.User currentUser;

        private final class CheckForTap implements Runnable {
            public void run() {
                if (pendingCheckForLongPress == null) {
                    pendingCheckForLongPress = new CheckForLongPress();
                }
                pendingCheckForLongPress.currentPressCount = ++pressCount;
                postDelayed(pendingCheckForLongPress, ViewConfiguration.getLongPressTimeout() - ViewConfiguration.getTapTimeout());
            }
        }

        class CheckForLongPress implements Runnable {
            public int currentPressCount;

            public void run() {
                if (checkingForLongPress && getParent() != null && currentPressCount == pressCount) {
                    checkingForLongPress = false;
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    onLongPress();
                    MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    onTouchEvent(event);
                    event.recycle();
                }
            }
        }

        public AttachBotButton(Context context) {
            super(context);

            imageView = new BackupImageView(context);
            imageView.setRoundRadius(AndroidUtilities.dp(27));
            addView(imageView, LayoutHelper.createFrame(54, 54, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 7, 0, 0));

            nameTextView = new TextView(context);
            nameTextView.setTextColor(Theme.ATTACH_SHEET_TEXT_COLOR);
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            nameTextView.setMaxLines(2);
            nameTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            nameTextView.setLines(2);
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 6, 65, 6, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(85), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.EXACTLY));
        }

        private void onLongPress() {
            if (baseFragment == null || currentUser == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            builder.setMessage(LocaleController.formatString("ChatHintsDelete", R.string.ChatHintsDelete, ContactsController.formatName(currentUser.first_name, currentUser.last_name)));
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    SearchQuery.removeInline(currentUser.id);
                }
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            builder.show();
        }

        public void setUser(TLRPC.User user) {
            if (user == null) {
                return;
            }
            currentUser = user;
            TLRPC.FileLocation photo = null;
            nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
            avatarDrawable.setInfo(user);
            if (user != null && user.photo != null) {
                photo = user.photo.photo_small;
            }
            imageView.setImage(photo, "50_50", avatarDrawable);
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            boolean result = false;

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressed = true;
                invalidate();
                result = true;
            } else if (pressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    pressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    delegate.didSelectBot(MessagesController.getInstance().getUser(SearchQuery.inlineBots.get((Integer) getTag()).peer.user_id));
                    setUseRevealAnimation(false);
                    dismiss();
                    setUseRevealAnimation(true);
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    pressed = false;
                    invalidate();
                }
            }
            if (!result) {
                result = super.onTouchEvent(event);
            } else {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startCheckLongPress();
                }
            }
            if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) {
                cancelCheckLongPress();
            }

            return result;
        }

        protected void startCheckLongPress() {
            if (checkingForLongPress) {
                return;
            }
            checkingForLongPress = true;
            if (pendingCheckForTap == null) {
                pendingCheckForTap = new CheckForTap();
            }
            postDelayed(pendingCheckForTap, ViewConfiguration.getTapTimeout());
        }

        protected void cancelCheckLongPress() {
            checkingForLongPress = false;
            if (pendingCheckForLongPress != null) {
                removeCallbacks(pendingCheckForLongPress);
            }
            if (pendingCheckForTap != null) {
                removeCallbacks(pendingCheckForTap);
            }
        }
    }

    public ChatAttachAlert(Context context, final ChatActivity parentFragment) {
        super(context, false);
        baseFragment = parentFragment;
        setDelegate(this);
        setUseRevealAnimation(true);
        checkCamera(false);
        if (deviceHasGoodCamera) {
            CameraController.getInstance().initCamera();
        }
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.albumsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.reloadInlineHints);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.cameraInitied);
        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow);

        containerView = listView = new RecyclerListView(context) {

            private int lastWidth;
            private int lastHeight;

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (cameraAnimationInProgress) {
                    return true;
                } else if (cameraOpened) {
                    return processTouchEvent(ev);
                } else if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY) {
                    dismiss();
                    return true;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (cameraAnimationInProgress) {
                    return true;
                } else if (cameraOpened) {
                    return processTouchEvent(event);
                }
                return !isDismissed() && super.onTouchEvent(event);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = MeasureSpec.getSize(heightMeasureSpec);
                if (Build.VERSION.SDK_INT >= 21) {
                    height -= AndroidUtilities.statusBarHeight;
                }
                int contentSize = backgroundPaddingTop + AndroidUtilities.dp(294) + (SearchQuery.inlineBots.isEmpty() ? 0 : ((int) Math.ceil(SearchQuery.inlineBots.size() / 4.0f) * AndroidUtilities.dp(100) + AndroidUtilities.dp(12)));
                int padding = contentSize == AndroidUtilities.dp(294) ? 0 : Math.max(0, (height - AndroidUtilities.dp(294)));
                if (padding != 0 && contentSize < height) {
                    padding -= (height - contentSize);
                }
                if (padding == 0) {
                    padding = backgroundPaddingTop;
                }
                if (getPaddingTop() != padding) {
                    ignoreLayout = true;
                    setPadding(backgroundPaddingLeft, padding, backgroundPaddingLeft, 0);
                    ignoreLayout = false;
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.min(contentSize, height), MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int width = right - left;
                int height = bottom - top;

                int newPosition = -1;
                int newTop = 0;

                int count = listView.getChildCount();
                int lastVisibleItemPosition = -1;
                int lastVisibleItemPositionTop = 0;
                if (count > 0) {
                    View child = listView.getChildAt(listView.getChildCount() - 1);
                    Holder holder = (Holder) listView.findContainingViewHolder(child);
                    if (holder != null) {
                        lastVisibleItemPosition = holder.getAdapterPosition();
                        lastVisibleItemPositionTop = child.getTop();
                    }
                }

                if (lastVisibleItemPosition >= 0 && height - lastHeight != 0) {
                    newPosition = lastVisibleItemPosition;
                    newTop = lastVisibleItemPositionTop + height - lastHeight - getPaddingTop();
                }

                super.onLayout(changed, left, top, right, bottom);

                if (newPosition != -1) {
                    ignoreLayout = true;
                    layoutManager.scrollToPositionWithOffset(newPosition, newTop);
                    super.onLayout(false, left, top, right, bottom);
                    ignoreLayout = false;
                }

                lastHeight = height;
                lastWidth = width;

                updateLayout();
                checkCameraViewPosition();
            }

            @Override
            public void onDraw(Canvas canvas) {
                if (useRevealAnimation && Build.VERSION.SDK_INT <= 19) {
                    canvas.save();
                    canvas.clipRect(backgroundPaddingLeft, scrollOffsetY, getMeasuredWidth() - backgroundPaddingLeft, getMeasuredHeight());
                    if (revealAnimationInProgress) {
                        canvas.drawCircle(revealX, revealY, revealRadius, ciclePaint);
                    } else {
                        canvas.drawRect(backgroundPaddingLeft, scrollOffsetY, getMeasuredWidth() - backgroundPaddingLeft, getMeasuredHeight(), ciclePaint);
                    }
                    canvas.restore();
                } else {
                    shadowDrawable.setBounds(0, scrollOffsetY - backgroundPaddingTop, getMeasuredWidth(), getMeasuredHeight());
                    shadowDrawable.draw(canvas);
                }
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                checkCameraViewPosition();
            }
        };

        listView.setWillNotDraw(false);
        listView.setClipToPadding(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(getContext()));
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setEnabled(true);
        listView.setGlowColor(0xfff5f6f7);
        listView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                outRect.left = 0;
                outRect.right = 0;
                outRect.top = 0;
                outRect.bottom = 0;
            }
        });

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (listView.getChildCount() <= 0) {
                    return;
                }
                if (hintShowed) {
                    if (layoutManager.findLastVisibleItemPosition() > 1) {
                        hideHint();
                        hintShowed = false;
                        ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putBoolean("bothint", true).commit();
                    }
                }
                updateLayout();
                checkCameraViewPosition();
            }
        });
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        attachView = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(294), MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int width = right - left;
                int height = bottom - top;
                int t = AndroidUtilities.dp(8);
                attachPhotoRecyclerView.layout(0, t, width, t + attachPhotoRecyclerView.getMeasuredHeight());
                progressView.layout(0, t, width, t + progressView.getMeasuredHeight());
                lineView.layout(0, AndroidUtilities.dp(96), width, AndroidUtilities.dp(96) + lineView.getMeasuredHeight());
                hintTextView.layout(width - hintTextView.getMeasuredWidth() - AndroidUtilities.dp(5), height - hintTextView.getMeasuredHeight() - AndroidUtilities.dp(5), width - AndroidUtilities.dp(5), height - AndroidUtilities.dp(5));

                int diff = (width - AndroidUtilities.dp(85 * 4 + 20)) / 3;
                for (int a = 0; a < 8; a++) {
                    int y = AndroidUtilities.dp(105 + 95 * (a / 4));
                    int x = AndroidUtilities.dp(10) + (a % 4) * (AndroidUtilities.dp(85) + diff);
                    views[a].layout(x, y, x + views[a].getMeasuredWidth(), y + views[a].getMeasuredHeight());
                }
            }
        };

        views[8] = attachPhotoRecyclerView = new RecyclerListView(context);
        attachPhotoRecyclerView.setVerticalScrollBarEnabled(true);
        attachPhotoRecyclerView.setAdapter(photoAttachAdapter = new PhotoAttachAdapter(context));
        attachPhotoRecyclerView.setClipToPadding(false);
        attachPhotoRecyclerView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        attachPhotoRecyclerView.setItemAnimator(null);
        attachPhotoRecyclerView.setLayoutAnimation(null);
        attachPhotoRecyclerView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        attachView.addView(attachPhotoRecyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80));
        attachPhotoLayoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        attachPhotoLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        attachPhotoRecyclerView.setLayoutManager(attachPhotoLayoutManager);
        attachPhotoRecyclerView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @SuppressWarnings("unchecked")
            @Override
            public void onItemClick(View view, int position) {
                if (baseFragment == null || baseFragment.getParentActivity() == null) {
                    return;
                }
                if (!deviceHasGoodCamera || position != 0) {
                    if (deviceHasGoodCamera) {
                        position--;
                    }
                    if (MediaController.allPhotosAlbumEntry == null) {
                        return;
                    }
                    ArrayList<Object> arrayList = (ArrayList) MediaController.allPhotosAlbumEntry.photos;
                    if (position < 0 || position >= arrayList.size()) {
                        return;
                    }
                    PhotoViewer.getInstance().setParentActivity(baseFragment.getParentActivity());
                    PhotoViewer.getInstance().openPhotoForSelect(arrayList, position, 0, ChatAttachAlert.this, baseFragment);
                    AndroidUtilities.hideKeyboard(baseFragment.getFragmentView().findFocus());
                } else {
                    openCamera();
                }
            }
        });
        attachPhotoRecyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                checkCameraViewPosition();
            }
        });

        views[9] = progressView = new EmptyTextProgressView(context);
        if (Build.VERSION.SDK_INT >= 23 && getContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            progressView.setText(LocaleController.getString("PermissionStorage", R.string.PermissionStorage));
            progressView.setTextSize(16);
        } else {
            progressView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
            progressView.setTextSize(20);
        }
        attachView.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80));
        attachPhotoRecyclerView.setEmptyView(progressView);

        views[10] = lineView = new View(getContext()) {
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        lineView.setBackgroundColor(0xffd2d2d2);
        attachView.addView(lineView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.TOP | Gravity.LEFT));
        CharSequence[] items = new CharSequence[]{
                LocaleController.getString("ChatCamera", R.string.ChatCamera),
                LocaleController.getString("ChatGallery", R.string.ChatGallery),
                LocaleController.getString("ChatVideo", R.string.ChatVideo),
                LocaleController.getString("AttachMusic", R.string.AttachMusic),
                LocaleController.getString("ChatDocument", R.string.ChatDocument),
                LocaleController.getString("AttachContact", R.string.AttachContact),
                LocaleController.getString("ChatLocation", R.string.ChatLocation),
                ""
        };
        for (int a = 0; a < 8; a++) {
            AttachButton attachButton = new AttachButton(context);
            attachButton.setTextAndIcon(items[a], Theme.attachButtonDrawables[a]);
            attachView.addView(attachButton, LayoutHelper.createFrame(85, 90, Gravity.LEFT | Gravity.TOP));
            attachButton.setTag(a);
            views[a] = attachButton;
            if (a == 7) {
                sendPhotosButton = attachButton;
                sendPhotosButton.imageView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
            }
            attachButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    delegate.didPressedButton((Integer) v.getTag());
                }
            });
        }

        hintTextView = new TextView(context);
        hintTextView.setBackgroundResource(R.drawable.tooltip);
        hintTextView.setTextColor(Theme.CHAT_GIF_HINT_TEXT_COLOR);
        hintTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hintTextView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        hintTextView.setText(LocaleController.getString("AttachBotsHelp", R.string.AttachBotsHelp));
        hintTextView.setGravity(Gravity.CENTER_VERTICAL);
        hintTextView.setVisibility(View.INVISIBLE);
        hintTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.scroll_tip, 0, 0, 0);
        hintTextView.setCompoundDrawablePadding(AndroidUtilities.dp(8));
        attachView.addView(hintTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, Gravity.RIGHT | Gravity.BOTTOM, 5, 0, 5, 5));

        for (int a = 0; a < 8; a++) {
            viewsCache.add(photoAttachAdapter.createHolder());
        }

        if (loading) {
            progressView.showProgress();
        } else {
            progressView.showTextView();
        }

        if (Build.VERSION.SDK_INT >= 16) {
            recordTime = new TextView(context);
            recordTime.setBackgroundResource(R.drawable.system);
            recordTime.getBackground().setColorFilter(new PorterDuffColorFilter(0x66000000, PorterDuff.Mode.MULTIPLY));
            recordTime.setText("00:00");
            recordTime.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            recordTime.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            recordTime.setAlpha(0.0f);
            recordTime.setTextColor(0xffffffff);
            recordTime.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(10), AndroidUtilities.dp(5));
            container.addView(recordTime, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16, 0, 0));

            cameraPanel = new FrameLayout(context) {
                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    int cx = getMeasuredWidth() / 2;
                    int cy = getMeasuredHeight() / 2;
                    int cx2;
                    int cy2;
                    shutterButton.layout(cx - shutterButton.getMeasuredWidth() / 2, cy - shutterButton.getMeasuredHeight() / 2, cx + shutterButton.getMeasuredWidth() / 2, cy + shutterButton.getMeasuredHeight() / 2);
                    if (getMeasuredWidth() == AndroidUtilities.dp(100)) {
                        cx = cx2 = getMeasuredWidth() / 2;
                        cy2 = cy + cy / 2 + AndroidUtilities.dp(17);
                        cy = cy / 2 - AndroidUtilities.dp(17);
                    } else {
                        cx2 = cx + cx / 2 + AndroidUtilities.dp(17);
                        cx = cx / 2 - AndroidUtilities.dp(17);
                        cy = cy2 = getMeasuredHeight() / 2;
                    }
                    switchCameraButton.layout(cx2 - switchCameraButton.getMeasuredWidth() / 2, cy2 - switchCameraButton.getMeasuredHeight() / 2, cx2 + switchCameraButton.getMeasuredWidth() / 2, cy2 + switchCameraButton.getMeasuredHeight() / 2);
                    for (int a = 0; a < 2; a++) {
                        flashModeButton[a].layout(cx - flashModeButton[a].getMeasuredWidth() / 2, cy - flashModeButton[a].getMeasuredHeight() / 2, cx + flashModeButton[a].getMeasuredWidth() / 2, cy + flashModeButton[a].getMeasuredHeight() / 2);
                    }
                }
            };
            cameraPanel.setVisibility(View.GONE);
            cameraPanel.setAlpha(0.0f);
            container.addView(cameraPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.LEFT | Gravity.BOTTOM));

            shutterButton = new ShutterButton(context);
            cameraPanel.addView(shutterButton, LayoutHelper.createFrame(84, 84, Gravity.CENTER));
            shutterButton.setDelegate(new ShutterButton.ShutterButtonDelegate() {
                @Override
                public void shutterLongPressed() {
                    if (takingPhoto || baseFragment == null || baseFragment.getParentActivity() == null) {
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= 23) {
                        if (baseFragment.getParentActivity().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            baseFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 21);
                            return;
                        }
                    }
                    for (int a = 0; a < 2; a++) {
                        flashModeButton[a].setAlpha(0.0f);
                    }
                    switchCameraButton.setAlpha(0.0f);
                    cameraFile = AndroidUtilities.generateVideoPath();
                    recordTime.setAlpha(1.0f);
                    recordTime.setText("00:00");
                    videoRecordTime = 0;
                    videoRecordRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (videoRecordRunnable == null) {
                                return;
                            }
                            videoRecordTime++;
                            recordTime.setText(String.format("%02d:%02d", videoRecordTime / 60, videoRecordTime % 60));
                            AndroidUtilities.runOnUIThread(videoRecordRunnable, 1000);
                        }
                    };
                    AndroidUtilities.lockOrientation(parentFragment.getParentActivity());
                    CameraController.getInstance().recordVideo(cameraView.getCameraSession(), cameraFile, new CameraController.VideoTakeCallback() {
                        @Override
                        public void onFinishVideoRecording(final Bitmap thumb) {
                            if (cameraFile == null || baseFragment == null) {
                                return;
                            }
                            PhotoViewer.getInstance().setParentActivity(baseFragment.getParentActivity());
                            cameraPhoto = new ArrayList<>();
                            cameraPhoto.add(new MediaController.PhotoEntry(0, 0, 0, cameraFile.getAbsolutePath(), 0, true));
                            PhotoViewer.getInstance().openPhotoForSelect(cameraPhoto, 0, 2, new PhotoViewer.EmptyPhotoViewerProvider() {
                                @Override
                                public Bitmap getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
                                    return thumb;
                                }

                                @TargetApi(16)
                                @Override
                                public boolean cancelButtonPressed() {
                                    if (cameraOpened && cameraView != null && cameraFile != null) {
                                        cameraFile.delete();
                                        AndroidUtilities.runOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (cameraView != null && !isDismissed() && Build.VERSION.SDK_INT >= 21) {
                                                    cameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
                                                }
                                            }
                                        }, 1000);
                                        CameraController.getInstance().startPreview(cameraView.getCameraSession());
                                        cameraFile = null;
                                    }
                                    return true;
                                }

                                @Override
                                public void sendButtonPressed(int index) {
                                    if (cameraFile == null) {
                                        return;
                                    }
                                    AndroidUtilities.addMediaToGallery(cameraFile.getAbsolutePath());
                                    baseFragment.sendMedia((MediaController.PhotoEntry) cameraPhoto.get(0), PhotoViewer.getInstance().isMuteVideo());
                                    closeCamera(false);
                                    dismiss();
                                    cameraFile = null;
                                }
                            }, baseFragment);
                        }
                    });
                    AndroidUtilities.runOnUIThread(videoRecordRunnable, 1000);
                    shutterButton.setState(ShutterButton.State.RECORDING, true);
                }

                @Override
                public void shutterCancel() {
                    cameraFile.delete();
                    resetRecordState();
                    CameraController.getInstance().stopVideoRecording(cameraView.getCameraSession(), true);
                }

                @Override
                public void shutterReleased() {
                    if (takingPhoto) {
                        return;
                    }
                    if (shutterButton.getState() == ShutterButton.State.RECORDING) {
                        resetRecordState();
                        CameraController.getInstance().stopVideoRecording(cameraView.getCameraSession(), false);
                        shutterButton.setState(ShutterButton.State.DEFAULT, true);
                        return;
                    }
                    cameraFile = AndroidUtilities.generatePicturePath();
                    takingPhoto = CameraController.getInstance().takePicture(cameraFile, cameraView.getCameraSession(), new Runnable() {
                        @Override
                        public void run() {
                            takingPhoto = false;
                            if (cameraFile == null || baseFragment == null) {
                                return;
                            }
                            PhotoViewer.getInstance().setParentActivity(baseFragment.getParentActivity());
                            cameraPhoto = new ArrayList<>();
                            int orientation = 0;
                            try {
                                ExifInterface ei = new ExifInterface(cameraFile.getAbsolutePath());
                                int exif = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                                switch (exif) {
                                    case ExifInterface.ORIENTATION_ROTATE_90:
                                        orientation = 90;
                                        break;
                                    case ExifInterface.ORIENTATION_ROTATE_180:
                                        orientation = 180;
                                        break;
                                    case ExifInterface.ORIENTATION_ROTATE_270:
                                        orientation = 270;
                                        break;
                                }
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                            cameraPhoto.add(new MediaController.PhotoEntry(0, 0, 0, cameraFile.getAbsolutePath(), orientation, false));
                            PhotoViewer.getInstance().openPhotoForSelect(cameraPhoto, 0, 2, new PhotoViewer.EmptyPhotoViewerProvider() {
                                @TargetApi(16)
                                @Override
                                public boolean cancelButtonPressed() {
                                    if (cameraOpened && cameraView != null && cameraFile != null) {
                                        cameraFile.delete();
                                        AndroidUtilities.runOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (cameraView != null && !isDismissed() && Build.VERSION.SDK_INT >= 21) {
                                                    cameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
                                                }
                                            }
                                        }, 1000);
                                        CameraController.getInstance().startPreview(cameraView.getCameraSession());
                                        cameraFile = null;
                                    }
                                    return true;
                                }

                                @Override
                                public void sendButtonPressed(int index) {
                                    if (cameraFile == null) {
                                        return;
                                    }
                                    AndroidUtilities.addMediaToGallery(cameraFile.getAbsolutePath());
                                    baseFragment.sendMedia((MediaController.PhotoEntry) cameraPhoto.get(0), false);
                                    closeCamera(false);
                                    dismiss();
                                    cameraFile = null;
                                }

                                @Override
                                public boolean scaleToFill() {
                                    return true;
                                }
                            }, baseFragment);
                        }
                    });
                }
            });

            switchCameraButton = new ImageView(context);
            switchCameraButton.setScaleType(ImageView.ScaleType.CENTER);
            cameraPanel.addView(switchCameraButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
            switchCameraButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (takingPhoto || cameraView == null || !cameraView.isInitied()) {
                        return;
                    }
                    cameraInitied = false;
                    cameraView.switchCamera();
                    ObjectAnimator animator = ObjectAnimator.ofFloat(switchCameraButton, "scaleX", 0.0f).setDuration(100);
                    animator.addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationEnd(Animator animator) {
                            switchCameraButton.setImageResource(cameraView.isFrontface() ? R.drawable.camera_revert1 : R.drawable.camera_revert2);
                            ObjectAnimator.ofFloat(switchCameraButton, "scaleX", 1.0f).setDuration(100).start();
                        }
                    });
                    animator.start();
                }
            });

            for (int a = 0; a < 2; a++) {
                flashModeButton[a] = new ImageView(context);
                flashModeButton[a].setScaleType(ImageView.ScaleType.CENTER);
                flashModeButton[a].setVisibility(View.INVISIBLE);
                cameraPanel.addView(flashModeButton[a], LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
                flashModeButton[a].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View currentImage) {
                        if (flashAnimationInProgress || cameraView == null || !cameraView.isInitied() || !cameraOpened) {
                            return;
                        }
                        String current = cameraView.getCameraSession().getCurrentFlashMode();
                        String next = cameraView.getCameraSession().getNextFlashMode();
                        if (current.equals(next)) {
                            return;
                        }
                        cameraView.getCameraSession().setCurrentFlashMode(next);
                        flashAnimationInProgress = true;
                        ImageView nextImage = flashModeButton[0] == currentImage ? flashModeButton[1] : flashModeButton[0];
                        nextImage.setVisibility(View.VISIBLE);
                        setCameraFlashModeIcon(nextImage, next);
                        AnimatorSet animatorSet = new AnimatorSet();
                        animatorSet.playTogether(
                                ObjectAnimator.ofFloat(currentImage, "translationY", 0, AndroidUtilities.dp(48)),
                                ObjectAnimator.ofFloat(nextImage, "translationY", -AndroidUtilities.dp(48), 0),
                                ObjectAnimator.ofFloat(currentImage, "alpha", 1.0f, 0.0f),
                                ObjectAnimator.ofFloat(nextImage, "alpha", 0.0f, 1.0f));
                        animatorSet.setDuration(200);
                        animatorSet.addListener(new AnimatorListenerAdapterProxy() {
                            @Override
                            public void onAnimationEnd(Animator animator) {
                                flashAnimationInProgress = false;
                                currentImage.setVisibility(View.INVISIBLE);
                            }
                        });
                        animatorSet.start();
                    }
                });
            }
        }
    }

    private boolean processTouchEvent(MotionEvent event) {
        if (!pressed && event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            if (!takingPhoto) {
                pressed = true;
                maybeStartDraging = true;
                lastY = event.getY();
            }
        } else if (pressed) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float newY = event.getY();
                float dy = (newY - lastY);
                if (maybeStartDraging) {
                    if (Math.abs(dy) > AndroidUtilities.getPixelsInCM(0.4f, false)) {
                        maybeStartDraging = false;
                    }
                } else {
                    cameraView.setTranslationY(cameraView.getTranslationY() + dy);
                    lastY = newY;
                    if (cameraPanel.getTag() == null) {
                        cameraPanel.setTag(1);
                        AnimatorSet animatorSet = new AnimatorSet();
                        animatorSet.playTogether(
                                ObjectAnimator.ofFloat(cameraPanel, "alpha", 0.0f),
                                ObjectAnimator.ofFloat(flashModeButton[0], "alpha", 0.0f),
                                ObjectAnimator.ofFloat(flashModeButton[1], "alpha", 0.0f));
                        animatorSet.setDuration(200);
                        animatorSet.start();
                    }
                }
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
                pressed = false;
                if (Math.abs(cameraView.getTranslationY()) > cameraView.getMeasuredHeight() / 6.0f) {
                    closeCamera(true);
                } else {
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(cameraView, "translationY", 0.0f),
                            ObjectAnimator.ofFloat(cameraPanel, "alpha", 1.0f),
                            ObjectAnimator.ofFloat(flashModeButton[0], "alpha", 1.0f),
                            ObjectAnimator.ofFloat(flashModeButton[1], "alpha", 1.0f));
                    animatorSet.setDuration(250);
                    animatorSet.setInterpolator(interpolator);
                    animatorSet.start();
                    cameraPanel.setTag(null);
                }
            }
        }
        return true;
    }

    @Override
    protected boolean onContainerTouchEvent(MotionEvent event) {
        return cameraOpened && processTouchEvent(event);
    }

    private void resetRecordState() {
        if (baseFragment == null) {
            return;
        }
        for (int a = 0; a < 2; a++) {
            flashModeButton[a].setAlpha(1.0f);
        }
        switchCameraButton.setAlpha(1.0f);
        recordTime.setAlpha(0.0f);
        AndroidUtilities.cancelRunOnUIThread(videoRecordRunnable);
        videoRecordRunnable = null;
        AndroidUtilities.unlockOrientation(baseFragment.getParentActivity());
    }

    private void setCameraFlashModeIcon(ImageView imageView, String mode) {
        switch (mode) {
            case Camera.Parameters.FLASH_MODE_OFF:
                imageView.setImageResource(R.drawable.flash_off);
                break;
            case Camera.Parameters.FLASH_MODE_ON:
                imageView.setImageResource(R.drawable.flash_on);
                break;
            case Camera.Parameters.FLASH_MODE_AUTO:
                imageView.setImageResource(R.drawable.flash_auto);
                break;
        }
    }

    @Override
    protected boolean onCustomMeasure(View view, int width, int height) {
        boolean isPortrait = width < height;
        if (view == cameraView) {
            if (cameraOpened && !cameraAnimationInProgress) {
                cameraView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                return true;
            }
        } else if (view == cameraPanel) {
            if (isPortrait) {
                cameraPanel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), View.MeasureSpec.EXACTLY));
            } else {
                cameraPanel.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean onCustomLayout(View view, int left, int top, int right, int bottom) {
        int width = (right - left);
        int height = (bottom - top);
        boolean isPortrait = width < height;
        if (view == cameraPanel) {
            if (isPortrait) {
                cameraPanel.layout(0, bottom - AndroidUtilities.dp(100), width, bottom);
            } else {
                cameraPanel.layout(right - AndroidUtilities.dp(100), 0, right, height);
            }
            return true;
        } else if (view == flashModeButton[0] || view == flashModeButton[1]) {
            int topAdd = Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.dp(10) : 0;
            int leftAdd = Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.dp(8) : 0;
            if (isPortrait) {
                view.layout(right - view.getMeasuredWidth() - leftAdd, topAdd, right - leftAdd, topAdd + view.getMeasuredHeight());
            } else {
                view.layout(leftAdd, topAdd, leftAdd + view.getMeasuredWidth(), topAdd + view.getMeasuredHeight());
            }
            return true;
        }
        return false;
    }

    private void hideHint() {
        if (hideHintRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideHintRunnable);
            hideHintRunnable = null;
        }
        if (hintTextView == null) {
            return;
        }
        currentHintAnimation = new AnimatorSet();
        currentHintAnimation.playTogether(
                ObjectAnimator.ofFloat(hintTextView, "alpha", 0.0f)
        );
        currentHintAnimation.setInterpolator(decelerateInterpolator);
        currentHintAnimation.addListener(new AnimatorListenerAdapterProxy() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (currentHintAnimation == null || !currentHintAnimation.equals(animation)) {
                    return;
                }
                currentHintAnimation = null;
                if (hintTextView != null) {
                    hintTextView.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (currentHintAnimation != null && currentHintAnimation.equals(animation)) {
                    currentHintAnimation = null;
                }
            }
        });
        currentHintAnimation.setDuration(300);
        currentHintAnimation.start();
    }

    public void onPause() {
        if (!cameraOpened || shutterButton.getState() != ShutterButton.State.RECORDING) {
            return;
        }
        resetRecordState();
        CameraController.getInstance().stopVideoRecording(cameraView.getCameraSession(), false);
        shutterButton.setState(ShutterButton.State.DEFAULT, true);
    }

    @TargetApi(16)
    private void openCamera() {
        if (cameraView == null) {
            return;
        }
        animateCameraValues[0] = 0;
        animateCameraValues[1] = AndroidUtilities.dp(80) - cameraViewOffsetX;
        animateCameraValues[2] = AndroidUtilities.dp(80) - cameraViewOffsetY;
        cameraAnimationInProgress = true;
        cameraPanel.setVisibility(View.VISIBLE);
        cameraPanel.setTag(null);
        ArrayList<Animator> animators = new ArrayList<>();
        animators.add(ObjectAnimator.ofFloat(ChatAttachAlert.this, "cameraOpenProgress", 0.0f, 1.0f));
        animators.add(ObjectAnimator.ofFloat(cameraPanel, "alpha", 1.0f));
        for (int a = 0; a < 2; a++) {
            if (flashModeButton[a].getVisibility() == View.VISIBLE) {
                animators.add(ObjectAnimator.ofFloat(flashModeButton[a], "alpha", 1.0f));
                break;
            }
        }
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(animators);
        animatorSet.setDuration(200);
        animatorSet.addListener(new AnimatorListenerAdapterProxy() {
            @Override
            public void onAnimationEnd(Animator animator) {
                cameraAnimationInProgress = false;
            }
        });
        animatorSet.start();
        if (Build.VERSION.SDK_INT >= 21) {
            cameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
        cameraOpened = true;
    }

    @TargetApi(16)
    public void closeCamera(boolean animated) {
        if (takingPhoto || cameraView == null) {
            return;
        }
        animateCameraValues[1] = AndroidUtilities.dp(80) - cameraViewOffsetX;
        animateCameraValues[2] = AndroidUtilities.dp(80) - cameraViewOffsetY;
        if (animated) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) cameraView.getLayoutParams();
            animateCameraValues[0] = layoutParams.topMargin = (int) cameraView.getTranslationY();
            cameraView.setLayoutParams(layoutParams);
            cameraView.setTranslationY(0);

            cameraAnimationInProgress = true;
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(ChatAttachAlert.this, "cameraOpenProgress", 0.0f));
            animators.add(ObjectAnimator.ofFloat(cameraPanel, "alpha", 0.0f));
            for (int a = 0; a < 2; a++) {
                if (flashModeButton[a].getVisibility() == View.VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(flashModeButton[a], "alpha", 0.0f));
                    break;
                }
            }
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(animators);
            animatorSet.setDuration(200);
            animatorSet.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    cameraAnimationInProgress = false;
                    cameraPanel.setVisibility(View.GONE);
                    cameraOpened = false;
                    if (Build.VERSION.SDK_INT >= 21) {
                        cameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                    }
                }
            });
            animatorSet.start();
        } else {
            animateCameraValues[0] = 0;
            setCameraOpenProgress(0);
            cameraPanel.setAlpha(0);
            cameraPanel.setVisibility(View.GONE);
            for (int a = 0; a < 2; a++) {
                if (flashModeButton[a].getVisibility() == View.VISIBLE) {
                    flashModeButton[a].setAlpha(0.0f);
                    break;
                }
            }
            cameraOpened = false;
            if (Build.VERSION.SDK_INT >= 21) {
                cameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            }
        }
    }

    public void setCameraOpenProgress(float value) {
        if (cameraView == null) {
            return;
        }
        cameraOpenProgress = value;
        float startWidth = animateCameraValues[1];
        float startHeight = animateCameraValues[2];
        boolean isPortrait = AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y;
        float endWidth;
        float endHeight;
        if (isPortrait) {
            endWidth = container.getWidth();
            endHeight = container.getHeight()/* - AndroidUtilities.dp(100)*/;
        } else {
            endWidth = container.getWidth()/* - AndroidUtilities.dp(100)*/;
            endHeight = container.getHeight();
        }
        if (value == 0) {
            cameraView.setClipLeft(cameraViewOffsetX);
            cameraView.setClipTop(cameraViewOffsetY);
            cameraView.setTranslationX(cameraViewLocation[0]);
            cameraView.setTranslationY(cameraViewLocation[1]);
            cameraIcon.setTranslationX(cameraViewLocation[0]);
            cameraIcon.setTranslationY(cameraViewLocation[1]);
        } else if (cameraView.getTranslationX() != 0 || cameraView.getTranslationY() != 0) {
            cameraView.setTranslationX(0);
            cameraView.setTranslationY(0);
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) cameraView.getLayoutParams();
        layoutParams.width = (int) (startWidth + (endWidth - startWidth) * value);
        layoutParams.height = (int) (startHeight + (endHeight - startHeight) * value);
        if (value != 0) {
            cameraView.setClipLeft((int) (cameraViewOffsetX * (1.0f - value)));
            cameraView.setClipTop((int) (cameraViewOffsetY * (1.0f - value)));
            layoutParams.leftMargin = (int) (cameraViewLocation[0] * (1.0f - value));
            layoutParams.topMargin = (int) (animateCameraValues[0] + (cameraViewLocation[1] - animateCameraValues[0]) * (1.0f - value));
        } else {
            layoutParams.leftMargin = 0;
            layoutParams.topMargin = 0;
        }
        cameraView.setLayoutParams(layoutParams);
        if (value <= 0.5f) {
            cameraIcon.setAlpha(1.0f - value / 0.5f);
        } else {
            cameraIcon.setAlpha(0.0f);
        }
    }

    public float getCameraOpenProgress() {
        return cameraOpenProgress;
    }

    private void checkCameraViewPosition() {
        if (!deviceHasGoodCamera) {
            return;
        }
        int count = attachPhotoRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = attachPhotoRecyclerView.getChildAt(a);
            if (child instanceof PhotoAttachCameraCell) {
                if (Build.VERSION.SDK_INT >= 19) {
                    if (!child.isAttachedToWindow()) {
                        break;
                    }
                }
                child.getLocationInWindow(cameraViewLocation);
                float listViewX = listView.getX() + backgroundPaddingLeft;
                if (cameraViewLocation[0] < listViewX) {
                    cameraViewOffsetX = (int) (listViewX - cameraViewLocation[0]);
                    if (cameraViewOffsetX >= AndroidUtilities.dp(80)) {
                        cameraViewOffsetX = 0;
                        cameraViewLocation[0] = AndroidUtilities.dp(-100);
                        cameraViewLocation[1] = 0;
                    } else {
                        cameraViewLocation[0] += cameraViewOffsetX;
                    }
                } else {
                    cameraViewOffsetX = 0;
                }
                if (Build.VERSION.SDK_INT >= 21 && cameraViewLocation[1] < AndroidUtilities.statusBarHeight) {
                    cameraViewOffsetY = AndroidUtilities.statusBarHeight - cameraViewLocation[1];
                    if (cameraViewOffsetY >= AndroidUtilities.dp(80)) {
                        cameraViewOffsetY = 0;
                        cameraViewLocation[0] = AndroidUtilities.dp(-100);
                        cameraViewLocation[1] = 0;
                    } else {
                        cameraViewLocation[1] += cameraViewOffsetY;
                    }
                } else {
                    cameraViewOffsetY = 0;
                }
                applyCameraViewPosition();
                return;
            }
        }
        cameraViewOffsetX = 0;
        cameraViewOffsetY = 0;
        cameraViewLocation[0] = AndroidUtilities.dp(-100);
        cameraViewLocation[1] = 0;
        applyCameraViewPosition();
    }

    private void applyCameraViewPosition() {
        if (cameraView != null) {
            if (!cameraOpened) {
                cameraView.setTranslationX(cameraViewLocation[0]);
                cameraView.setTranslationY(cameraViewLocation[1]);
            }
            cameraIcon.setTranslationX(cameraViewLocation[0]);
            cameraIcon.setTranslationY(cameraViewLocation[1]);
            int finalWidth = AndroidUtilities.dp(80) - cameraViewOffsetX;
            int finalHeight = AndroidUtilities.dp(80) - cameraViewOffsetY;

            FrameLayout.LayoutParams layoutParams;
            if (!cameraOpened) {
                cameraView.setClipLeft(cameraViewOffsetX);
                cameraView.setClipTop(cameraViewOffsetY);
                layoutParams = (FrameLayout.LayoutParams) cameraView.getLayoutParams();
                if (layoutParams.height != finalHeight || layoutParams.width != finalWidth) {
                    layoutParams.width = finalWidth;
                    layoutParams.height = finalHeight;
                    cameraView.setLayoutParams(layoutParams);
                    final FrameLayout.LayoutParams layoutParamsFinal = layoutParams;
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (cameraView != null) {
                                cameraView.setLayoutParams(layoutParamsFinal);
                            }
                        }
                    });
                }
            }

            layoutParams = (FrameLayout.LayoutParams) cameraIcon.getLayoutParams();
            if (layoutParams.height != finalHeight || layoutParams.width != finalWidth) {
                layoutParams.width = finalWidth;
                layoutParams.height = finalHeight;
                cameraIcon.setLayoutParams(layoutParams);
                final FrameLayout.LayoutParams layoutParamsFinal = layoutParams;
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (cameraIcon != null) {
                            cameraIcon.setLayoutParams(layoutParamsFinal);
                        }
                    }
                });
            }
        }
    }

    @TargetApi(16)
    public void showCamera() {
        if (cameraView == null) {
            cameraView = new CameraView(baseFragment.getParentActivity());
            container.addView(cameraView, 1, LayoutHelper.createFrame(80, 80));
            cameraView.setDelegate(new CameraView.CameraViewDelegate() {
                @Override
                public void onCameraInit() {
                    int count = attachPhotoRecyclerView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = attachPhotoRecyclerView.getChildAt(a);
                        if (child instanceof PhotoAttachCameraCell) {
                            child.setVisibility(View.INVISIBLE);
                            break;
                        }
                    }

                    String current = cameraView.getCameraSession().getCurrentFlashMode();
                    String next = cameraView.getCameraSession().getNextFlashMode();
                    if (current.equals(next)) {
                        for (int a = 0; a < 2; a++) {
                            flashModeButton[a].setVisibility(View.INVISIBLE);
                            flashModeButton[a].setAlpha(0.0f);
                            flashModeButton[a].setTranslationY(0.0f);
                        }
                    } else {
                        setCameraFlashModeIcon(flashModeButton[0], cameraView.getCameraSession().getCurrentFlashMode());
                        for (int a = 0; a < 2; a++) {
                            flashModeButton[a].setVisibility(a == 0 ? View.VISIBLE : View.INVISIBLE);
                            flashModeButton[a].setAlpha(a == 0 && cameraOpened ? 1.0f : 0.0f);
                            flashModeButton[a].setTranslationY(0.0f);
                        }
                    }
                    switchCameraButton.setImageResource(cameraView.isFrontface() ? R.drawable.camera_revert1 : R.drawable.camera_revert2);
                    switchCameraButton.setVisibility(cameraView.hasFrontFaceCamera() ? View.VISIBLE : View.INVISIBLE);
                }
            });

            cameraIcon = new FrameLayout(baseFragment.getParentActivity());
            container.addView(cameraIcon, 2, LayoutHelper.createFrame(80, 80));

            ImageView cameraImageView = new ImageView(baseFragment.getParentActivity());
            cameraImageView.setScaleType(ImageView.ScaleType.CENTER);
            cameraImageView.setImageResource(R.drawable.instant_camera);
            cameraIcon.addView(cameraImageView, LayoutHelper.createFrame(80, 80, Gravity.RIGHT | Gravity.BOTTOM));
        }
        cameraView.setTranslationX(cameraViewLocation[0]);
        cameraView.setTranslationY(cameraViewLocation[1]);
        cameraIcon.setTranslationX(cameraViewLocation[0]);
        cameraIcon.setTranslationY(cameraViewLocation[1]);
    }

    public void hideCamera(boolean async) {
        if (!deviceHasGoodCamera || cameraView == null) {
            return;
        }
        cameraView.destroy(async);
        container.removeView(cameraView);
        container.removeView(cameraIcon);
        cameraView = null;
        cameraIcon = null;
        int count = attachPhotoRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = attachPhotoRecyclerView.getChildAt(a);
            if (child instanceof PhotoAttachCameraCell) {
                child.setVisibility(View.VISIBLE);
                return;
            }
        }
    }

    private void showHint() {
        if (SearchQuery.inlineBots.isEmpty()) {
            return;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        if (preferences.getBoolean("bothint", false)) {
            return;
        }
        hintShowed = true;

        hintTextView.setVisibility(View.VISIBLE);
        currentHintAnimation = new AnimatorSet();
        currentHintAnimation.playTogether(
                ObjectAnimator.ofFloat(hintTextView, "alpha", 0.0f, 1.0f)
        );
        currentHintAnimation.setInterpolator(decelerateInterpolator);
        currentHintAnimation.addListener(new AnimatorListenerAdapterProxy() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (currentHintAnimation == null || !currentHintAnimation.equals(animation)) {
                    return;
                }
                currentHintAnimation = null;
                AndroidUtilities.runOnUIThread(hideHintRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (hideHintRunnable != this) {
                            return;
                        }
                        hideHintRunnable = null;
                        hideHint();
                    }
                }, 2000);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (currentHintAnimation != null && currentHintAnimation.equals(animation)) {
                    currentHintAnimation = null;
                }
            }
        });
        currentHintAnimation.setDuration(300);
        currentHintAnimation.start();
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.albumsDidLoaded) {
            if (photoAttachAdapter != null) {
                loading = false;
                progressView.showTextView();
                photoAttachAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.reloadInlineHints) {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.cameraInitied) {
            checkCamera(false);
        }
    }

    @SuppressLint("NewApi")
    private void updateLayout() {
        if (listView.getChildCount() <= 0) {
            listView.setTopGlowOffset(scrollOffsetY = listView.getPaddingTop());
            listView.invalidate();
            return;
        }
        View child = listView.getChildAt(0);
        Holder holder = (Holder) listView.findContainingViewHolder(child);
        int top = child.getTop();
        int newOffset = 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
        }
        if (scrollOffsetY != newOffset) {
            listView.setTopGlowOffset(scrollOffsetY = newOffset);
            listView.invalidate();
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    public void updatePhotosButton() {
        int count = photoAttachAdapter.getSelectedPhotos().size();
        if (count == 0) {
            sendPhotosButton.imageView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
            sendPhotosButton.imageView.setBackgroundResource(R.drawable.attach_hide_states);
            sendPhotosButton.imageView.setImageResource(R.drawable.attach_hide2);
            sendPhotosButton.textView.setText("");
        } else {
            sendPhotosButton.imageView.setPadding(AndroidUtilities.dp(2), 0, 0, 0);
            sendPhotosButton.imageView.setBackgroundResource(R.drawable.attach_send_states);
            sendPhotosButton.imageView.setImageResource(R.drawable.attach_send2);
            sendPhotosButton.textView.setText(LocaleController.formatString("SendItems", R.string.SendItems, String.format("(%d)", count)));
        }

        if (Build.VERSION.SDK_INT >= 23 && getContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            progressView.setText(LocaleController.getString("PermissionStorage", R.string.PermissionStorage));
            progressView.setTextSize(16);
        } else {
            progressView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
            progressView.setTextSize(20);
        }
    }

    public void setDelegate(ChatAttachViewDelegate chatAttachViewDelegate) {
        delegate = chatAttachViewDelegate;
    }

    public void loadGalleryPhotos() {
        if (MediaController.allPhotosAlbumEntry == null && Build.VERSION.SDK_INT >= 21) {
            MediaController.loadGalleryPhotosAlbums(0);
        }
    }

    public void init() {
        if (MediaController.allPhotosAlbumEntry != null) {
            for (int a = 0; a < Math.min(100, MediaController.allPhotosAlbumEntry.photos.size()); a++) {
                MediaController.PhotoEntry photoEntry = MediaController.allPhotosAlbumEntry.photos.get(a);
                photoEntry.caption = null;
                photoEntry.imagePath = null;
                photoEntry.thumbPath = null;
                photoEntry.stickers.clear();
            }
        }
        if (currentHintAnimation != null) {
            currentHintAnimation.cancel();
            currentHintAnimation = null;
        }
        hintTextView.setAlpha(0.0f);
        hintTextView.setVisibility(View.INVISIBLE);
        attachPhotoLayoutManager.scrollToPositionWithOffset(0, 1000000);
        photoAttachAdapter.clearSelectedPhotos();
        layoutManager.scrollToPositionWithOffset(0, 1000000);
        updatePhotosButton();
    }

    public HashMap<Integer, MediaController.PhotoEntry> getSelectedPhotos() {
        return photoAttachAdapter.getSelectedPhotos();
    }

    public void onDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.albumsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.reloadInlineHints);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.cameraInitied);
        baseFragment = null;
    }

    private PhotoAttachPhotoCell getCellForIndex(int index) {
        if (MediaController.allPhotosAlbumEntry == null) {
            return null;
        }
        int count = attachPhotoRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = attachPhotoRecyclerView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                int num = (Integer) cell.getImageView().getTag();
                if (num < 0 || num >= MediaController.allPhotosAlbumEntry.photos.size()) {
                    continue;
                }
                if (num == index) {
                    return cell;
                }
            }
        }
        return null;
    }

    @Override
    public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        PhotoAttachPhotoCell cell = getCellForIndex(index);
        if (cell != null) {
            int coords[] = new int[2];
            cell.getImageView().getLocationInWindow(coords);
            PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
            object.viewX = coords[0];
            object.viewY = coords[1];
            object.parentView = attachPhotoRecyclerView;
            object.imageReceiver = cell.getImageView().getImageReceiver();
            object.thumb = object.imageReceiver.getBitmap();
            object.scale = cell.getImageView().getScaleX();
            cell.getCheckBox().setVisibility(View.GONE);
            return object;
        }
        return null;
    }

    @Override
    public boolean scaleToFill() {
        return false;
    }

    @Override
    public boolean allowCaption() {
        return true;
    }

    @Override
    public void updatePhotoAtIndex(int index) {
        PhotoAttachPhotoCell cell = getCellForIndex(index);
        if (cell != null) {
            cell.getImageView().setOrientation(0, true);
            MediaController.PhotoEntry photoEntry = MediaController.allPhotosAlbumEntry.photos.get(index);
            if (photoEntry.thumbPath != null) {
                cell.getImageView().setImage(photoEntry.thumbPath, null, cell.getContext().getResources().getDrawable(R.drawable.nophotos));
            } else if (photoEntry.path != null) {
                cell.getImageView().setOrientation(photoEntry.orientation, true);
                cell.getImageView().setImage("thumb://" + photoEntry.imageId + ":" + photoEntry.path, null, cell.getContext().getResources().getDrawable(R.drawable.nophotos));
            } else {
                cell.getImageView().setImageResource(R.drawable.nophotos);
            }
        }
    }

    @Override
    public Bitmap getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        PhotoAttachPhotoCell cell = getCellForIndex(index);
        if (cell != null) {
            return cell.getImageView().getImageReceiver().getBitmap();
        }
        return null;
    }

    @Override
    public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        PhotoAttachPhotoCell cell = getCellForIndex(index);
        if (cell != null) {
            cell.getCheckBox().setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void willHidePhotoViewer() {
        int count = attachPhotoRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = attachPhotoRecyclerView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                if (cell.getCheckBox().getVisibility() != View.VISIBLE) {
                    cell.getCheckBox().setVisibility(View.VISIBLE);
                }
            }
        }
    }

    @Override
    public boolean isPhotoChecked(int index) {
        return !(index < 0 || index >= MediaController.allPhotosAlbumEntry.photos.size()) && photoAttachAdapter.getSelectedPhotos().containsKey(MediaController.allPhotosAlbumEntry.photos.get(index).imageId);
    }

    @Override
    public void setPhotoChecked(int index) {
        boolean add = true;
        if (index < 0 || index >= MediaController.allPhotosAlbumEntry.photos.size()) {
            return;
        }
        MediaController.PhotoEntry photoEntry = MediaController.allPhotosAlbumEntry.photos.get(index);
        if (photoAttachAdapter.getSelectedPhotos().containsKey(photoEntry.imageId)) {
            photoAttachAdapter.getSelectedPhotos().remove(photoEntry.imageId);
            add = false;
        } else {
            photoAttachAdapter.getSelectedPhotos().put(photoEntry.imageId, photoEntry);
        }
        int count = attachPhotoRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = attachPhotoRecyclerView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                int num = (Integer) view.getTag();
                if (num == index) {
                    ((PhotoAttachPhotoCell) view).setChecked(add, false);
                    break;
                }
            }
        }
        updatePhotosButton();
    }

    @Override
    public boolean cancelButtonPressed() {
        return false;
    }

    @Override
    public void sendButtonPressed(int index) {
        if (photoAttachAdapter.getSelectedPhotos().isEmpty()) {
            if (index < 0 || index >= MediaController.allPhotosAlbumEntry.photos.size()) {
                return;
            }
            MediaController.PhotoEntry photoEntry = MediaController.allPhotosAlbumEntry.photos.get(index);
            photoAttachAdapter.getSelectedPhotos().put(photoEntry.imageId, photoEntry);
        }
        delegate.didPressedButton(7);
    }

    @Override
    public int getSelectedCount() {
        return photoAttachAdapter.getSelectedPhotos().size();
    }

    private void onRevealAnimationEnd(boolean open) {
        NotificationCenter.getInstance().setAnimationInProgress(false);
        revealAnimationInProgress = false;
        if (open && Build.VERSION.SDK_INT <= 19 && MediaController.allPhotosAlbumEntry == null) {
            MediaController.loadGalleryPhotosAlbums(0);
        }
        if (open) {
            checkCamera(true);
            showHint();
        }
    }

    public void checkCamera(boolean request) {
        if (baseFragment == null) {
            return;
        }
        boolean old = deviceHasGoodCamera;
        if (Build.VERSION.SDK_INT >= 23) {
            if (baseFragment.getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                if (request) {
                    baseFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, 17);
                }
                deviceHasGoodCamera = false;
            } else {
                CameraController.getInstance().initCamera();
                deviceHasGoodCamera = CameraController.getInstance().isCameraInitied();
            }
        } else if (Build.VERSION.SDK_INT >= 16) {
            CameraController.getInstance().initCamera();
            deviceHasGoodCamera = CameraController.getInstance().isCameraInitied();
        }
        if (old != deviceHasGoodCamera && photoAttachAdapter != null) {
            photoAttachAdapter.notifyDataSetChanged();
        }
        if (isShowing() && deviceHasGoodCamera && baseFragment != null && !revealAnimationInProgress) {
            showCamera();
        }
    }

    @Override
    public void onOpenAnimationEnd() {
        onRevealAnimationEnd(true);
    }

    @Override
    public void onOpenAnimationStart() {

    }

    private class Holder extends RecyclerView.ViewHolder {

        public Holder(View itemView) {
            super(itemView);
        }
    }

    private class ListAdapter extends RecyclerView.Adapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = attachView;
                    break;
                case 1:
                    view = new ShadowSectionCell(mContext);
                    break;
                default:
                    FrameLayout frameLayout = new FrameLayout(mContext) {
                        @Override
                        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                            int diff = (right - left - AndroidUtilities.dp(85 * 4 + 20)) / 3;
                            for (int a = 0; a < 4; a++) {
                                int x = AndroidUtilities.dp(10) + (a % 4) * (AndroidUtilities.dp(85) + diff);
                                View child = getChildAt(a);
                                child.layout(x, 0, x + child.getMeasuredWidth(), child.getMeasuredHeight());
                            }
                        }
                    };
                    for (int a = 0; a < 4; a++) {
                        frameLayout.addView(new AttachBotButton(mContext));
                    }
                    view = frameLayout;
                    frameLayout.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(100)));
                    break;
            }
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (position > 1) {
                position -= 2;
                position *= 4;
                FrameLayout frameLayout = (FrameLayout) holder.itemView;
                for (int a = 0; a < 4; a++) {
                    AttachBotButton child = (AttachBotButton) frameLayout.getChildAt(a);
                    if (position + a >= SearchQuery.inlineBots.size()) {
                        child.setVisibility(View.INVISIBLE);
                    } else {
                        child.setVisibility(View.VISIBLE);
                        child.setTag(position + a);
                        child.setUser(MessagesController.getInstance().getUser(SearchQuery.inlineBots.get(position + a).peer.user_id));
                    }
                }
            }
        }

        @Override
        public int getItemCount() {
            return 1 + (!SearchQuery.inlineBots.isEmpty() ? 1 + (int) Math.ceil(SearchQuery.inlineBots.size() / 4.0f) : 0);
        }

        @Override
        public int getItemViewType(int position) {
            switch (position) {
                case 0:
                    return 0;
                case 1:
                    return 1;
                default:
                    return 2;
            }
        }
    }

    private class PhotoAttachAdapter extends RecyclerView.Adapter {

        private Context mContext;
        private HashMap<Integer, MediaController.PhotoEntry> selectedPhotos = new HashMap<>();

        public PhotoAttachAdapter(Context context) {
            mContext = context;
        }

        public void clearSelectedPhotos() {
            if (!selectedPhotos.isEmpty()) {
                for (HashMap.Entry<Integer, MediaController.PhotoEntry> entry : selectedPhotos.entrySet()) {
                    MediaController.PhotoEntry photoEntry = entry.getValue();
                    photoEntry.imagePath = null;
                    photoEntry.thumbPath = null;
                    photoEntry.caption = null;
                    photoEntry.stickers.clear();
                }
                selectedPhotos.clear();
                updatePhotosButton();
                notifyDataSetChanged();
            }
        }

        public Holder createHolder() {
            PhotoAttachPhotoCell cell = new PhotoAttachPhotoCell(mContext);
            cell.setDelegate(new PhotoAttachPhotoCell.PhotoAttachPhotoCellDelegate() {
                @Override
                public void onCheckClick(PhotoAttachPhotoCell v) {
                    MediaController.PhotoEntry photoEntry = v.getPhotoEntry();
                    if (selectedPhotos.containsKey(photoEntry.imageId)) {
                        selectedPhotos.remove(photoEntry.imageId);
                        v.setChecked(false, true);
                        photoEntry.imagePath = null;
                        photoEntry.thumbPath = null;
                        photoEntry.stickers.clear();
                        v.setPhotoEntry(photoEntry, (Integer) v.getTag() == MediaController.allPhotosAlbumEntry.photos.size() - 1);
                    } else {
                        selectedPhotos.put(photoEntry.imageId, photoEntry);
                        v.setChecked(true, true);
                    }
                    updatePhotosButton();
                }
            });
            return new Holder(cell);
        }

        public HashMap<Integer, MediaController.PhotoEntry> getSelectedPhotos() {
            return selectedPhotos;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (!deviceHasGoodCamera || position != 0) {
                if (deviceHasGoodCamera) {
                    position--;
                }
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) holder.itemView;
                MediaController.PhotoEntry photoEntry = MediaController.allPhotosAlbumEntry.photos.get(position);
                cell.setPhotoEntry(photoEntry, position == MediaController.allPhotosAlbumEntry.photos.size() - 1);
                cell.setChecked(selectedPhotos.containsKey(photoEntry.imageId), false);
                cell.getImageView().setTag(position);
                cell.setTag(position);
            } else if (deviceHasGoodCamera && position == 0) {
                if (cameraView != null && cameraView.isInitied()) {
                    holder.itemView.setVisibility(View.INVISIBLE);
                } else {
                    holder.itemView.setVisibility(View.VISIBLE);
                }
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Holder holder;
            switch (viewType) {
                case 1:
                    holder = new Holder(new PhotoAttachCameraCell(mContext));
                    break;
                default:
                    if (!viewsCache.isEmpty()) {
                        holder = viewsCache.get(0);
                        viewsCache.remove(0);
                    } else {
                        holder = createHolder();
                    }
                    break;
            }

            return holder;
        }

        @Override
        public int getItemCount() {
            int count = 0;
            if (deviceHasGoodCamera) {
                count++;
            }
            if (MediaController.allPhotosAlbumEntry != null) {
                count += MediaController.allPhotosAlbumEntry.photos.size();
            }
            return count;
        }

        @Override
        public int getItemViewType(int position) {
            if (deviceHasGoodCamera && position == 0) {
                return 1;
            }
            return 0;
        }
    }

    private void setUseRevealAnimation(boolean value) {
        if (!value || value && Build.VERSION.SDK_INT >= 18 && !AndroidUtilities.isTablet()) {
            useRevealAnimation = value;
        }
    }

    @SuppressLint("NewApi")
    protected void setRevealRadius(float radius) {
        revealRadius = radius;
        if (Build.VERSION.SDK_INT <= 19) {
            listView.invalidate();
        }
        if (!isDismissed()) {
            for (int a = 0; a < innerAnimators.size(); a++) {
                InnerAnimator innerAnimator = innerAnimators.get(a);
                if (innerAnimator.startRadius > radius) {
                    continue;
                }
                innerAnimator.animatorSet.start();
                innerAnimators.remove(a);
                a--;
            }
        }
    }

    protected float getRevealRadius() {
        return revealRadius;
    }

    @SuppressLint("NewApi")
    private void startRevealAnimation(final boolean open) {
        containerView.setTranslationY(0);

        final AnimatorSet animatorSet = new AnimatorSet();

        View view = delegate.getRevealView();
        if (view.getVisibility() == View.VISIBLE && ((ViewGroup) view.getParent()).getVisibility() == View.VISIBLE) {
            final int coords[] = new int[2];
            view.getLocationInWindow(coords);
            float top;
            if (Build.VERSION.SDK_INT <= 19) {
                top = AndroidUtilities.displaySize.y - containerView.getMeasuredHeight() - AndroidUtilities.statusBarHeight;
            } else {
                top = containerView.getY();
            }
            revealX = coords[0] + view.getMeasuredWidth() / 2;
            revealY = (int) (coords[1] + view.getMeasuredHeight() / 2 - top);
            if (Build.VERSION.SDK_INT <= 19) {
                revealY -= AndroidUtilities.statusBarHeight;
            }
        } else {
            revealX = AndroidUtilities.displaySize.x / 2 + backgroundPaddingLeft;
            revealY = (int) (AndroidUtilities.displaySize.y - containerView.getY());
        }

        int corners[][] = new int[][]{
                {0, 0},
                {0, AndroidUtilities.dp(304)},
                {containerView.getMeasuredWidth(), 0},
                {containerView.getMeasuredWidth(), AndroidUtilities.dp(304)}
        };
        int finalRevealRadius = 0;
        int y = revealY - scrollOffsetY + backgroundPaddingTop;
        for (int a = 0; a < 4; a++) {
            finalRevealRadius = Math.max(finalRevealRadius, (int) Math.ceil(Math.sqrt((revealX - corners[a][0]) * (revealX - corners[a][0]) + (y - corners[a][1]) * (y - corners[a][1]))));
        }
        int finalRevealX = revealX <= containerView.getMeasuredWidth() ? revealX : containerView.getMeasuredWidth();

        ArrayList<Animator> animators = new ArrayList<>(3);
        animators.add(ObjectAnimator.ofFloat(this, "revealRadius", open ? 0 : finalRevealRadius, open ? finalRevealRadius : 0));
        animators.add(ObjectAnimator.ofInt(backDrawable, "alpha", open ? 51 : 0));
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                animators.add(ViewAnimationUtils.createCircularReveal(containerView, finalRevealX, revealY, open ? 0 : finalRevealRadius, open ? finalRevealRadius : 0));
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            animatorSet.setDuration(320);
        } else {
            if (!open) {
                animatorSet.setDuration(200);
                containerView.setPivotX(revealX <= containerView.getMeasuredWidth() ? revealX : containerView.getMeasuredWidth());
                containerView.setPivotY(revealY);
                animators.add(ObjectAnimator.ofFloat(containerView, "scaleX", 0.0f));
                animators.add(ObjectAnimator.ofFloat(containerView, "scaleY", 0.0f));
                animators.add(ObjectAnimator.ofFloat(containerView, "alpha", 0.0f));
            } else {
                animatorSet.setDuration(250);
                containerView.setScaleX(1);
                containerView.setScaleY(1);
                containerView.setAlpha(1);
                if (Build.VERSION.SDK_INT <= 19) {
                    animatorSet.setStartDelay(20);
                }
            }
        }
        animatorSet.playTogether(animators);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                    currentSheetAnimation = null;
                    onRevealAnimationEnd(open);
                    containerView.invalidate();
                    containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (!open) {
                        try {
                            dismissInternal();
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (currentSheetAnimation != null && animatorSet.equals(animation)) {
                    currentSheetAnimation = null;
                }
            }
        });

        if (open) {
            innerAnimators.clear();
            NotificationCenter.getInstance().setAllowedNotificationsDutingAnimation(new int[]{NotificationCenter.dialogsNeedReload});
            NotificationCenter.getInstance().setAnimationInProgress(true);
            revealAnimationInProgress = true;

            int count = Build.VERSION.SDK_INT <= 19 ? 11 : 8;
            for (int a = 0; a < count; a++) {
                if (Build.VERSION.SDK_INT <= 19) {
                    if (a < 8) {
                        views[a].setScaleX(0.1f);
                        views[a].setScaleY(0.1f);
                    }
                    views[a].setAlpha(0.0f);
                } else {
                    views[a].setScaleX(0.7f);
                    views[a].setScaleY(0.7f);
                }

                InnerAnimator innerAnimator = new InnerAnimator();

                int buttonX = views[a].getLeft() + views[a].getMeasuredWidth() / 2;
                int buttonY = views[a].getTop() + attachView.getTop() + views[a].getMeasuredHeight() / 2;
                float dist = (float) Math.sqrt((revealX - buttonX) * (revealX - buttonX) + (revealY - buttonY) * (revealY - buttonY));
                float vecX = (revealX - buttonX) / dist;
                float vecY = (revealY - buttonY) / dist;
                views[a].setPivotX(views[a].getMeasuredWidth() / 2 + vecX * AndroidUtilities.dp(20));
                views[a].setPivotY(views[a].getMeasuredHeight() / 2 + vecY * AndroidUtilities.dp(20));
                innerAnimator.startRadius = dist - AndroidUtilities.dp(27 * 3);

                views[a].setTag(R.string.AppName, 1);
                animators = new ArrayList<>();
                final AnimatorSet animatorSetInner;
                if (a < 8) {
                    animators.add(ObjectAnimator.ofFloat(views[a], "scaleX", 0.7f, 1.05f));
                    animators.add(ObjectAnimator.ofFloat(views[a], "scaleY", 0.7f, 1.05f));

                    animatorSetInner = new AnimatorSet();
                    animatorSetInner.playTogether(
                            ObjectAnimator.ofFloat(views[a], "scaleX", 1.0f),
                            ObjectAnimator.ofFloat(views[a], "scaleY", 1.0f));
                    animatorSetInner.setDuration(100);
                    animatorSetInner.setInterpolator(decelerateInterpolator);
                } else {
                    animatorSetInner = null;
                }
                if (Build.VERSION.SDK_INT <= 19) {
                    animators.add(ObjectAnimator.ofFloat(views[a], "alpha", 1.0f));
                }
                innerAnimator.animatorSet = new AnimatorSet();
                innerAnimator.animatorSet.playTogether(animators);
                innerAnimator.animatorSet.setDuration(150);
                innerAnimator.animatorSet.setInterpolator(decelerateInterpolator);
                innerAnimator.animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animatorSetInner != null) {
                            animatorSetInner.start();
                        }
                    }
                });
                innerAnimators.add(innerAnimator);
            }
        }
        currentSheetAnimation = animatorSet;
        animatorSet.start();
    }

    @Override
    public void dismissInternal() {
        if (containerView != null) {
            containerView.setVisibility(View.INVISIBLE);
        }
        super.dismissInternal();
    }

    @Override
    protected boolean onCustomOpenAnimation() {
        if (useRevealAnimation) {
            startRevealAnimation(true);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onCustomCloseAnimation() {
        if (useRevealAnimation) {
            backDrawable.setAlpha(51);
            startRevealAnimation(false);
            return true;
        }
        return false;
    }

    @Override
    public void dismissWithButtonClick(int item) {
        super.dismissWithButtonClick(item);
        hideCamera(item != 0 && item != 2);
    }

    @Override
    protected boolean canDismissWithTouchOutside() {
        return !cameraOpened;
    }

    @Override
    public void dismiss() {
        if (cameraAnimationInProgress) {
            return;
        }
        if (cameraOpened) {
            closeCamera(true);
            return;
        }
        hideCamera(true);
        super.dismiss();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (cameraOpened && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            shutterButton.getDelegate().shutterReleased();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
