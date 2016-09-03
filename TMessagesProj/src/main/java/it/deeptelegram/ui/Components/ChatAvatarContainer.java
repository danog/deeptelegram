/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import it.deeptelegram.messenger.AndroidUtilities;
import it.deeptelegram.messenger.ChatObject;
import it.deeptelegram.messenger.FileLog;
import it.deeptelegram.messenger.LocaleController;
import it.deeptelegram.messenger.MessagesController;
import it.deeptelegram.messenger.R;
import it.deeptelegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ProfileActivity;

public class ChatAvatarContainer extends FrameLayout {

    private BackupImageView avatarImageView;
    private SimpleTextView titleTextView;
    private SimpleTextView subtitleTextView;
    private ImageView timeItem;
    private TimerDrawable timerDrawable;
    private ChatActivity parentFragment;
    private TypingDotsDrawable typingDotsDrawable;
    private RecordStatusDrawable recordStatusDrawable;
    private SendingFileExDrawable sendingFileDrawable;
    private AvatarDrawable avatarDrawable = new AvatarDrawable();

    private int onlineCount = -1;

    public ChatAvatarContainer(Context context, ChatActivity chatActivity, boolean needTime) {
        super(context);
        parentFragment = chatActivity;

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(21));
        addView(avatarImageView);

        titleTextView = new SimpleTextView(context);
        titleTextView.setTextColor(Theme.ACTION_BAR_TITLE_COLOR);
        titleTextView.setTextSize(18);
        titleTextView.setGravity(Gravity.LEFT);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f));
        titleTextView.setRightDrawableTopPadding(-AndroidUtilities.dp(1.3f));
        addView(titleTextView);

        subtitleTextView = new SimpleTextView(context);
        subtitleTextView.setTextColor(Theme.ACTION_BAR_SUBTITLE_COLOR);
        subtitleTextView.setTextSize(14);
        subtitleTextView.setGravity(Gravity.LEFT);
        addView(subtitleTextView);

        if (needTime) {
            timeItem = new ImageView(context);
            timeItem.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(5));
            timeItem.setScaleType(ImageView.ScaleType.CENTER);
            timeItem.setImageDrawable(timerDrawable = new TimerDrawable(context));
            addView(timeItem);
            timeItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    parentFragment.showDialog(AndroidUtilities.buildTTLAlert(getContext(), parentFragment.getCurrentEncryptedChat()).create());
                }
            });
        }

        setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TLRPC.User user = parentFragment.getCurrentUser();
                TLRPC.Chat chat = parentFragment.getCurrentChat();
                if (user != null) {
                    Bundle args = new Bundle();
                    args.putInt("user_id", user.id);
                    if (timeItem != null) {
                        args.putLong("dialog_id", parentFragment.getDialogId());
                    }
                    ProfileActivity fragment = new ProfileActivity(args);
                    fragment.setPlayProfileAnimation(true);
                    parentFragment.presentFragment(fragment);
                } else if (chat != null) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", chat.id);
                    ProfileActivity fragment = new ProfileActivity(args);
                    fragment.setChatInfo(parentFragment.getCurrentChatInfo());
                    fragment.setPlayProfileAnimation(true);
                    parentFragment.presentFragment(fragment);
                }
            }
        });

        TLRPC.Chat chat = parentFragment.getCurrentChat();
        typingDotsDrawable = new TypingDotsDrawable();
        typingDotsDrawable.setIsChat(chat != null);
        recordStatusDrawable = new RecordStatusDrawable();
        recordStatusDrawable.setIsChat(chat != null);
        sendingFileDrawable = new SendingFileExDrawable();
        sendingFileDrawable.setIsChat(chat != null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int availableWidth = width - AndroidUtilities.dp(54 + 16);
        avatarImageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY));
        titleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.AT_MOST));
        subtitleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.AT_MOST));
        if (timeItem != null) {
            timeItem.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(34), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(34), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int actionBarHeight = ActionBar.getCurrentActionBarHeight();
        int viewTop = (actionBarHeight - AndroidUtilities.dp(42)) / 2 + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
        avatarImageView.layout(AndroidUtilities.dp(8), viewTop, AndroidUtilities.dp(42 + 8), viewTop + AndroidUtilities.dp(42));
        titleTextView.layout(AndroidUtilities.dp(8 + 54), viewTop + AndroidUtilities.dp(1.3f), AndroidUtilities.dp(8 + 54) + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getTextHeight() + AndroidUtilities.dp(1.3f));
        if (timeItem != null) {
            timeItem.layout(AndroidUtilities.dp(8 + 16), viewTop + AndroidUtilities.dp(15), AndroidUtilities.dp(8 + 16 + 34), viewTop + AndroidUtilities.dp(15 + 34));
        }
        subtitleTextView.layout(AndroidUtilities.dp(8 + 54), viewTop + AndroidUtilities.dp(24), AndroidUtilities.dp(8 + 54) + subtitleTextView.getMeasuredWidth(), viewTop + subtitleTextView.getTextHeight() + AndroidUtilities.dp(24));
    }

    public void showTimeItem() {
        if (timeItem == null) {
            return;
        }
        timeItem.setVisibility(VISIBLE);
    }

    public void hideTimeItem() {
        if (timeItem == null) {
            return;
        }
        timeItem.setVisibility(GONE);
    }

    public void setTime(int value) {
        if (timerDrawable == null) {
            return;
        }
        timerDrawable.setTime(value);
    }

    public void setTitleIcons(int leftIcon, int rightIcon) {
        titleTextView.setLeftDrawable(leftIcon);
        titleTextView.setRightDrawable(rightIcon);
    }

    public void setTitle(CharSequence value) {
        titleTextView.setText(value);
    }

    private void setTypingAnimation(boolean start) {
        if (start) {
            try {
                Integer type = MessagesController.getInstance().printingStringsTypes.get(parentFragment.getDialogId());
                if (type == 0) {
                    subtitleTextView.setLeftDrawable(typingDotsDrawable);
                    typingDotsDrawable.start();
                    recordStatusDrawable.stop();
                    sendingFileDrawable.stop();
                } else if (type == 1) {
                    subtitleTextView.setLeftDrawable(recordStatusDrawable);
                    recordStatusDrawable.start();
                    typingDotsDrawable.stop();
                    sendingFileDrawable.stop();
                } else if (type == 2) {
                    subtitleTextView.setLeftDrawable(sendingFileDrawable);
                    sendingFileDrawable.start();
                    typingDotsDrawable.stop();
                    recordStatusDrawable.stop();
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        } else {
            subtitleTextView.setLeftDrawable(null);
            typingDotsDrawable.stop();
            recordStatusDrawable.stop();
            sendingFileDrawable.stop();
        }
    }

    public void updateSubtitle() {
        TLRPC.User user = parentFragment.getCurrentUser();
        TLRPC.Chat chat = parentFragment.getCurrentChat();
        CharSequence printString = MessagesController.getInstance().printingStrings.get(parentFragment.getDialogId());
        if (printString != null) {
            printString = TextUtils.replace(printString, new String[]{"..."}, new String[]{""});
        }
        if (printString == null || printString.length() == 0 || ChatObject.isChannel(chat) && !chat.megagroup) {
            setTypingAnimation(false);
            if (chat != null) {
                TLRPC.ChatFull info = parentFragment.getCurrentChatInfo();
                if (ChatObject.isChannel(chat)) {
                    if (info != null && info.participants_count != 0) {
                        if (chat.megagroup && info.participants_count <= 200) {
                            if (onlineCount > 1 && info.participants_count != 0) {
                                subtitleTextView.setText(String.format("%s, %s", LocaleController.formatPluralString("Members", info.participants_count), LocaleController.formatPluralString("Online", onlineCount)));
                            } else {
                                subtitleTextView.setText(LocaleController.formatPluralString("Members", info.participants_count));
                            }
                        } else {
                            int result[] = new int[1];
                            String shortNumber = LocaleController.formatShortNumber(info.participants_count, result);
                            String text = LocaleController.formatPluralString("Members", result[0]).replace(String.format("%d", result[0]), shortNumber);
                            subtitleTextView.setText(text);
                        }
                    } else {
                        if (chat.megagroup) {
                            subtitleTextView.setText(LocaleController.getString("Loading", R.string.Loading).toLowerCase());
                        } else {
                            if ((chat.flags & TLRPC.CHAT_FLAG_IS_PUBLIC) != 0) {
                                subtitleTextView.setText(LocaleController.getString("ChannelPublic", R.string.ChannelPublic).toLowerCase());
                            } else {
                                subtitleTextView.setText(LocaleController.getString("ChannelPrivate", R.string.ChannelPrivate).toLowerCase());
                            }
                        }
                    }
                } else {
                    if (ChatObject.isKickedFromChat(chat)) {
                        subtitleTextView.setText(LocaleController.getString("YouWereKicked", R.string.YouWereKicked));
                    } else if (ChatObject.isLeftFromChat(chat)) {
                        subtitleTextView.setText(LocaleController.getString("YouLeft", R.string.YouLeft));
                    } else {
                        int count = chat.participants_count;
                        if (info != null && info.participants != null) {
                            count = info.participants.participants.size();
                        }
                        if (onlineCount > 1 && count != 0) {
                            subtitleTextView.setText(String.format("%s, %s", LocaleController.formatPluralString("Members", count), LocaleController.formatPluralString("Online", onlineCount)));
                        } else {
                            subtitleTextView.setText(LocaleController.formatPluralString("Members", count));
                        }
                    }
                }
            } else if (user != null) {
                user = MessagesController.getInstance().getUser(user.id);
                String newStatus;
                if (user.id == 333000 || user.id == 777000) {
                    newStatus = LocaleController.getString("ServiceNotifications", R.string.ServiceNotifications);
                } else if (user.bot) {
                    newStatus = LocaleController.getString("Bot", R.string.Bot);
                } else {
                    newStatus = LocaleController.formatUserStatus(user);
                }
                subtitleTextView.setText(newStatus);
            }
        } else {
            subtitleTextView.setText(printString);
            setTypingAnimation(true);
        }
    }

    public void checkAndUpdateAvatar() {
        TLRPC.FileLocation newPhoto = null;
        TLRPC.User user = parentFragment.getCurrentUser();
        TLRPC.Chat chat = parentFragment.getCurrentChat();
        if (user != null) {
            if (user.photo != null) {
                newPhoto = user.photo.photo_small;
            }
            avatarDrawable.setInfo(user);
        } else if (chat != null) {
            if (chat.photo != null) {
                newPhoto = chat.photo.photo_small;
            }
            avatarDrawable.setInfo(chat);
        }
        if (avatarImageView != null) {
            avatarImageView.setImage(newPhoto, "50_50", avatarDrawable);
        }
    }

    public void updateOnlineCount() {
        onlineCount = 0;
        TLRPC.ChatFull info = parentFragment.getCurrentChatInfo();
        if (info == null) {
            return;
        }
        int currentTime = ConnectionsManager.getInstance().getCurrentTime();
        if (info instanceof TLRPC.TL_chatFull || info instanceof TLRPC.TL_channelFull && info.participants_count <= 200 && info.participants != null) {
            for (int a = 0; a < info.participants.participants.size(); a++) {
                TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                TLRPC.User user = MessagesController.getInstance().getUser(participant.user_id);
                if (user != null && user.status != null && (user.status.expires > currentTime || user.id == UserConfig.getClientUserId()) && user.status.expires > 10000) {
                    onlineCount++;
                }
            }
        }
    }
}
