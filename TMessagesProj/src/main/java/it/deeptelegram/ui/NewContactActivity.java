/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package it.deeptelegram.ui;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import it.deeptelegram.PhoneFormat.PhoneFormat;
import it.deeptelegram.messenger.AndroidUtilities;
import it.deeptelegram.messenger.AnimatorListenerAdapterProxy;
import it.deeptelegram.messenger.ApplicationLoader;
import it.deeptelegram.messenger.ContactsController;
import it.deeptelegram.messenger.FileLog;
import it.deeptelegram.messenger.LocaleController;
import it.deeptelegram.messenger.MessagesController;
import it.deeptelegram.messenger.R;
import it.deeptelegram.tgnet.ConnectionsManager;
import it.deeptelegram.tgnet.RequestDelegate;
import it.deeptelegram.tgnet.TLObject;
import it.deeptelegram.tgnet.TLRPC;
import it.deeptelegram.ui.ActionBar.ActionBar;
import it.deeptelegram.ui.ActionBar.ActionBarMenu;
import it.deeptelegram.ui.ActionBar.ActionBarMenuItem;
import it.deeptelegram.ui.ActionBar.BaseFragment;
import it.deeptelegram.ui.Components.AvatarDrawable;
import it.deeptelegram.ui.Components.BackupImageView;
import it.deeptelegram.ui.Components.ContextProgressView;
import it.deeptelegram.ui.Components.HintEditText;
import it.deeptelegram.ui.Components.LayoutHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import static android.widget.LinearLayout.HORIZONTAL;

public class NewContactActivity extends BaseFragment implements AdapterView.OnItemSelectedListener {

    private ActionBarMenuItem editDoneItem;
    private ContextProgressView editDoneItemProgress;
    private EditText firstNameField;
    private EditText lastNameField;
    private EditText codeField;
    private HintEditText phoneField;
    private BackupImageView avatarImage;
    private TextView countryButton;
    private AvatarDrawable avatarDrawable;
    private AnimatorSet editDoneItemAnimation;

    private ArrayList<String> countriesArray = new ArrayList<>();
    private HashMap<String, String> countriesMap = new HashMap<>();
    private HashMap<String, String> codesMap = new HashMap<>();
    private HashMap<String, String> phoneFormatMap = new HashMap<>();

    private boolean ignoreOnTextChange;
    private boolean ignoreOnPhoneChange;
    private int countryState;
    private boolean ignoreSelection;
    private boolean donePressed;

    private final static int done_button = 1;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("AddContactTitle", R.string.AddContactTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (donePressed) {
                        return;
                    }
                    if (firstNameField.length() == 0) {
                        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                        if (v != null) {
                            v.vibrate(200);
                        }
                        AndroidUtilities.shakeView(firstNameField, 2, 0);
                        return;
                    }
                    if (codeField.length() == 0) {
                        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                        if (v != null) {
                            v.vibrate(200);
                        }
                        AndroidUtilities.shakeView(codeField, 2, 0);
                        return;
                    }
                    if (phoneField.length() == 0) {
                        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                        if (v != null) {
                            v.vibrate(200);
                        }
                        AndroidUtilities.shakeView(phoneField, 2, 0);
                        return;
                    }
                    donePressed = true;
                    showEditDoneProgress(true, true);
                    TLRPC.TL_contacts_importContacts req = new TLRPC.TL_contacts_importContacts();
                    final TLRPC.TL_inputPhoneContact inputPhoneContact = new TLRPC.TL_inputPhoneContact();
                    inputPhoneContact.first_name = firstNameField.getText().toString();
                    inputPhoneContact.last_name = lastNameField.getText().toString();
                    inputPhoneContact.phone = "+" + codeField.getText().toString() + phoneField.getText().toString();
                    req.contacts.add(inputPhoneContact);
                    int reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                        @Override
                        public void run(TLObject response, final TLRPC.TL_error error) {
                            final TLRPC.TL_contacts_importedContacts res = (TLRPC.TL_contacts_importedContacts) response;
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    donePressed = false;
                                    if (res != null) {
                                        if (!res.users.isEmpty()) {
                                            MessagesController.getInstance().putUsers(res.users, false);
                                            MessagesController.openChatOrProfileWith(res.users.get(0), null, NewContactActivity.this, 1, true);
                                        } else {
                                            if (getParentActivity() == null) {
                                                return;
                                            }
                                            showEditDoneProgress(false, true);
                                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                            builder.setMessage(LocaleController.formatString("ContactNotRegistered", R.string.ContactNotRegistered, ContactsController.formatName(inputPhoneContact.first_name, inputPhoneContact.last_name)));
                                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                            builder.setPositiveButton(LocaleController.getString("Invite", R.string.Invite), new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    try {
                                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", inputPhoneContact.phone, null));
                                                        intent.putExtra("sms_body", LocaleController.getString("InviteText", R.string.InviteText));
                                                        getParentActivity().startActivityForResult(intent, 500);
                                                    } catch (Exception e) {
                                                        FileLog.e("tmessages", e);
                                                    }
                                                }
                                            });
                                            showDialog(builder.create());
                                        }
                                    } else {
                                        showEditDoneProgress(false, true);
                                        if (error == null || error.text.startsWith("FLOOD_WAIT")) {
                                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("FloodWait", R.string.FloodWait));
                                        } else {
                                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
                                        }
                                    }
                                }
                            });
                        }
                    }, ConnectionsManager.RequestFlagFailOnServerErrors);
                    ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
                }
            }
        });

        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(5, "", "", false);

        ActionBarMenu menu = actionBar.createMenu();
        editDoneItem = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

        editDoneItemProgress = new ContextProgressView(context, 1);
        editDoneItem.addView(editDoneItemProgress, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        editDoneItemProgress.setVisibility(View.INVISIBLE);

        fragmentView = new ScrollView(context);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        ((ScrollView) fragmentView).addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        linearLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 0));

        avatarImage = new BackupImageView(context);
        avatarImage.setImageDrawable(avatarDrawable);
        frameLayout.addView(avatarImage, LayoutHelper.createFrame(60, 60, Gravity.LEFT | Gravity.TOP, 0, 9, 0, 0));

        firstNameField = new EditText(context);
        firstNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        firstNameField.setHintTextColor(0xff979797);
        firstNameField.setTextColor(0xff212121);
        firstNameField.setMaxLines(1);
        firstNameField.setLines(1);
        firstNameField.setSingleLine(true);
        firstNameField.setGravity(Gravity.LEFT);
        firstNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        firstNameField.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        firstNameField.setHint(LocaleController.getString("FirstName", R.string.FirstName));
        AndroidUtilities.clearCursorDrawable(firstNameField);
        frameLayout.addView(firstNameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 34, Gravity.LEFT | Gravity.TOP, 84, 0, 0, 0));
        firstNameField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    lastNameField.requestFocus();
                    lastNameField.setSelection(lastNameField.length());
                    return true;
                }
                return false;
            }
        });
        firstNameField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                avatarDrawable.setInfo(5, firstNameField.getText().toString(), lastNameField.getText().toString(), false);
                avatarImage.invalidate();
            }
        });

        lastNameField = new EditText(context);
        lastNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        lastNameField.setHintTextColor(0xff979797);
        lastNameField.setTextColor(0xff212121);
        lastNameField.setMaxLines(1);
        lastNameField.setLines(1);
        lastNameField.setSingleLine(true);
        lastNameField.setGravity(Gravity.LEFT);
        lastNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        lastNameField.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        lastNameField.setHint(LocaleController.getString("LastName", R.string.LastName));
        AndroidUtilities.clearCursorDrawable(lastNameField);
        frameLayout.addView(lastNameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 34, Gravity.LEFT | Gravity.TOP, 84, 44, 0, 0));
        lastNameField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    phoneField.requestFocus();
                    phoneField.setSelection(phoneField.length());
                    return true;
                }
                return false;
            }
        });
        lastNameField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                avatarDrawable.setInfo(5, firstNameField.getText().toString(), lastNameField.getText().toString(), false);
                avatarImage.invalidate();
            }
        });

        countryButton = new TextView(context);
        countryButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        countryButton.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6), 0);
        countryButton.setTextColor(0xff212121);
        countryButton.setMaxLines(1);
        countryButton.setSingleLine(true);
        countryButton.setEllipsize(TextUtils.TruncateAt.END);
        countryButton.setGravity(Gravity.LEFT | Gravity.CENTER_HORIZONTAL);
        countryButton.setBackgroundResource(R.drawable.spinner_states);
        linearLayout.addView(countryButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0, 24, 0, 14));
        countryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CountrySelectActivity fragment = new CountrySelectActivity();
                fragment.setCountrySelectActivityDelegate(new CountrySelectActivity.CountrySelectActivityDelegate() {
                    @Override
                    public void didSelectCountry(String name) {
                        selectCountry(name);
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                AndroidUtilities.showKeyboard(phoneField);
                            }
                        }, 300);
                        phoneField.requestFocus();
                        phoneField.setSelection(phoneField.length());
                    }
                });
                presentFragment(fragment);
            }
        });

        View view = new View(context);
        view.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        view.setBackgroundColor(0xffdbdbdb);
        linearLayout.addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, -17.5f, 0, 0));

        LinearLayout linearLayout2 = new LinearLayout(context);
        linearLayout2.setOrientation(HORIZONTAL);
        linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

        TextView textView = new TextView(context);
        textView.setText("+");
        textView.setTextColor(0xff212121);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        linearLayout2.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        codeField = new EditText(context);
        codeField.setInputType(InputType.TYPE_CLASS_PHONE);
        codeField.setTextColor(0xff212121);
        AndroidUtilities.clearCursorDrawable(codeField);
        codeField.setPadding(AndroidUtilities.dp(10), 0, 0, 0);
        codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        codeField.setMaxLines(1);
        codeField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(5);
        codeField.setFilters(inputFilters);
        linearLayout2.addView(codeField, LayoutHelper.createLinear(55, 36, -9, 0, 16, 0));
        codeField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (ignoreOnTextChange) {
                    return;
                }
                ignoreOnTextChange = true;
                String text = PhoneFormat.stripExceptNumbers(codeField.getText().toString());
                codeField.setText(text);
                if (text.length() == 0) {
                    countryButton.setText(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                    phoneField.setHintText(null);
                    countryState = 1;
                } else {
                    String country;
                    boolean ok = false;
                    String textToSet = null;
                    if (text.length() > 4) {
                        ignoreOnTextChange = true;
                        for (int a = 4; a >= 1; a--) {
                            String sub = text.substring(0, a);
                            country = codesMap.get(sub);
                            if (country != null) {
                                ok = true;
                                textToSet = text.substring(a, text.length()) + phoneField.getText().toString();
                                codeField.setText(text = sub);
                                break;
                            }
                        }
                        if (!ok) {
                            ignoreOnTextChange = true;
                            textToSet = text.substring(1, text.length()) + phoneField.getText().toString();
                            codeField.setText(text = text.substring(0, 1));
                        }
                    }
                    country = codesMap.get(text);
                    if (country != null) {
                        int index = countriesArray.indexOf(country);
                        if (index != -1) {
                            ignoreSelection = true;
                            countryButton.setText(countriesArray.get(index));
                            String hint = phoneFormatMap.get(text);
                            phoneField.setHintText(hint != null ? hint.replace('X', '–') : null);
                            countryState = 0;
                        } else {
                            countryButton.setText(LocaleController.getString("WrongCountry", R.string.WrongCountry));
                            phoneField.setHintText(null);
                            countryState = 2;
                        }
                    } else {
                        countryButton.setText(LocaleController.getString("WrongCountry", R.string.WrongCountry));
                        phoneField.setHintText(null);
                        countryState = 2;
                    }
                    if (!ok) {
                        codeField.setSelection(codeField.getText().length());
                    }
                    if (textToSet != null) {
                        phoneField.requestFocus();
                        phoneField.setText(textToSet);
                        phoneField.setSelection(phoneField.length());
                    }
                }
                ignoreOnTextChange = false;
            }
        });
        codeField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    phoneField.requestFocus();
                    phoneField.setSelection(phoneField.length());
                    return true;
                }
                return false;
            }
        });

        phoneField = new HintEditText(context);
        phoneField.setInputType(InputType.TYPE_CLASS_PHONE);
        phoneField.setTextColor(0xff212121);
        phoneField.setHintTextColor(0xff979797);
        phoneField.setPadding(0, 0, 0, 0);
        AndroidUtilities.clearCursorDrawable(phoneField);
        phoneField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        phoneField.setMaxLines(1);
        phoneField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        phoneField.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        linearLayout2.addView(phoneField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36));
        phoneField.addTextChangedListener(new TextWatcher() {

            private int characterAction = -1;
            private int actionPosition;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (count == 0 && after == 1) {
                    characterAction = 1;
                } else if (count == 1 && after == 0) {
                    if (s.charAt(start) == ' ' && start > 0) {
                        characterAction = 3;
                        actionPosition = start - 1;
                    } else {
                        characterAction = 2;
                    }
                } else {
                    characterAction = -1;
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (ignoreOnPhoneChange) {
                    return;
                }
                int start = phoneField.getSelectionStart();
                String phoneChars = "0123456789";
                String str = phoneField.getText().toString();
                if (characterAction == 3) {
                    str = str.substring(0, actionPosition) + str.substring(actionPosition + 1, str.length());
                    start--;
                }
                StringBuilder builder = new StringBuilder(str.length());
                for (int a = 0; a < str.length(); a++) {
                    String ch = str.substring(a, a + 1);
                    if (phoneChars.contains(ch)) {
                        builder.append(ch);
                    }
                }
                ignoreOnPhoneChange = true;
                String hint = phoneField.getHintText();
                if (hint != null) {
                    for (int a = 0; a < builder.length(); a++) {
                        if (a < hint.length()) {
                            if (hint.charAt(a) == ' ') {
                                builder.insert(a, ' ');
                                a++;
                                if (start == a && characterAction != 2 && characterAction != 3) {
                                    start++;
                                }
                            }
                        } else {
                            builder.insert(a, ' ');
                            if (start == a + 1 && characterAction != 2 && characterAction != 3) {
                                start++;
                            }
                            break;
                        }
                    }
                }
                phoneField.setText(builder);
                if (start >= 0) {
                    phoneField.setSelection(start <= phoneField.length() ? start : phoneField.length());
                }
                phoneField.onTextChange();
                ignoreOnPhoneChange = false;
            }
        });
        phoneField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    editDoneItem.performClick();
                    return true;
                }
                return false;
            }
        });

        HashMap<String, String> languageMap = new HashMap<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().getAssets().open("countries.txt")));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] args = line.split(";");
                countriesArray.add(0, args[2]);
                countriesMap.put(args[2], args[0]);
                codesMap.put(args[0], args[2]);
                if (args.length > 3) {
                    phoneFormatMap.put(args[0], args[3]);
                }
                languageMap.put(args[1], args[2]);
            }
            reader.close();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        Collections.sort(countriesArray, new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                return lhs.compareTo(rhs);
            }
        });

        String country = null;

        try {
            TelephonyManager telephonyManager = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null) {
                country = telephonyManager.getSimCountryIso().toUpperCase();
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        if (country != null) {
            String countryName = languageMap.get(country);
            if (countryName != null) {
                int index = countriesArray.indexOf(countryName);
                if (index != -1) {
                    codeField.setText(countriesMap.get(countryName));
                    countryState = 0;
                }
            }
        }
        if (codeField.length() == 0) {
            countryButton.setText(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
            phoneField.setHintText(null);
            countryState = 1;
        }

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        boolean animations = preferences.getBoolean("view_animations", true);
        if (!animations) {
            firstNameField.requestFocus();
            AndroidUtilities.showKeyboard(firstNameField);
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            firstNameField.requestFocus();
            AndroidUtilities.showKeyboard(firstNameField);
        }
    }

    public void selectCountry(String name) {
        int index = countriesArray.indexOf(name);
        if (index != -1) {
            ignoreOnTextChange = true;
            String code = countriesMap.get(name);
            codeField.setText(code);
            countryButton.setText(name);
            String hint = phoneFormatMap.get(code);
            phoneField.setHintText(hint != null ? hint.replace('X', '–') : null);
            countryState = 0;
            ignoreOnTextChange = false;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        if (ignoreSelection) {
            ignoreSelection = false;
            return;
        }
        ignoreOnTextChange = true;
        String str = countriesArray.get(i);
        codeField.setText(countriesMap.get(str));
        ignoreOnTextChange = false;
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    private void showEditDoneProgress(final boolean show, boolean animated) {
        if (editDoneItemAnimation != null) {
            editDoneItemAnimation.cancel();
        }
        if (!animated) {
            if (show) {
                editDoneItem.getImageView().setScaleX(0.1f);
                editDoneItem.getImageView().setScaleY(0.1f);
                editDoneItem.getImageView().setAlpha(0.0f);
                editDoneItemProgress.setScaleX(1.0f);
                editDoneItemProgress.setScaleY(1.0f);
                editDoneItemProgress.setAlpha(1.0f);
                editDoneItem.getImageView().setVisibility(View.INVISIBLE);
                editDoneItemProgress.setVisibility(View.VISIBLE);
                editDoneItem.setEnabled(false);
            } else {
                editDoneItemProgress.setScaleX(0.1f);
                editDoneItemProgress.setScaleY(0.1f);
                editDoneItemProgress.setAlpha(0.0f);
                editDoneItem.getImageView().setScaleX(1.0f);
                editDoneItem.getImageView().setScaleY(1.0f);
                editDoneItem.getImageView().setAlpha(1.0f);
                editDoneItem.getImageView().setVisibility(View.VISIBLE);
                editDoneItemProgress.setVisibility(View.INVISIBLE);
                editDoneItem.setEnabled(true);
            }
        } else {
            editDoneItemAnimation = new AnimatorSet();
            if (show) {
                editDoneItemProgress.setVisibility(View.VISIBLE);
                editDoneItem.setEnabled(false);
                editDoneItemAnimation.playTogether(
                        ObjectAnimator.ofFloat(editDoneItem.getImageView(), "scaleX", 0.1f),
                        ObjectAnimator.ofFloat(editDoneItem.getImageView(), "scaleY", 0.1f),
                        ObjectAnimator.ofFloat(editDoneItem.getImageView(), "alpha", 0.0f),
                        ObjectAnimator.ofFloat(editDoneItemProgress, "scaleX", 1.0f),
                        ObjectAnimator.ofFloat(editDoneItemProgress, "scaleY", 1.0f),
                        ObjectAnimator.ofFloat(editDoneItemProgress, "alpha", 1.0f));
            } else {
                editDoneItem.getImageView().setVisibility(View.VISIBLE);
                editDoneItem.setEnabled(true);
                editDoneItemAnimation.playTogether(
                        ObjectAnimator.ofFloat(editDoneItemProgress, "scaleX", 0.1f),
                        ObjectAnimator.ofFloat(editDoneItemProgress, "scaleY", 0.1f),
                        ObjectAnimator.ofFloat(editDoneItemProgress, "alpha", 0.0f),
                        ObjectAnimator.ofFloat(editDoneItem.getImageView(), "scaleX", 1.0f),
                        ObjectAnimator.ofFloat(editDoneItem.getImageView(), "scaleY", 1.0f),
                        ObjectAnimator.ofFloat(editDoneItem.getImageView(), "alpha", 1.0f));

            }
            editDoneItemAnimation.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (editDoneItemAnimation != null && editDoneItemAnimation.equals(animation)) {
                        if (!show) {
                            editDoneItemProgress.setVisibility(View.INVISIBLE);
                        } else {
                            editDoneItem.getImageView().setVisibility(View.INVISIBLE);
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (editDoneItemAnimation != null && editDoneItemAnimation.equals(animation)) {
                        editDoneItemAnimation = null;
                    }
                }
            });
            editDoneItemAnimation.setDuration(150);
            editDoneItemAnimation.start();
        }
    }

    private void needShowAlert(String title, String text) {
        if (text == null || getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setMessage(text);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        showDialog(builder.create());
    }
}
