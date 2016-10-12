/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package it.deeptelegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import it.deeptelegram.messenger.LocaleController;
import it.deeptelegram.messenger.NotificationCenter;
import it.deeptelegram.messenger.R;
import it.deeptelegram.messenger.query.StickersQuery;
import it.deeptelegram.messenger.support.widget.LinearLayoutManager;
import it.deeptelegram.messenger.support.widget.RecyclerView;
import it.deeptelegram.tgnet.TLRPC;
import it.deeptelegram.ui.ActionBar.ActionBar;
import it.deeptelegram.ui.ActionBar.BaseFragment;
import it.deeptelegram.ui.Cells.FeaturedStickerSetCell;
import it.deeptelegram.ui.Cells.TextInfoPrivacyCell;
import it.deeptelegram.ui.Components.LayoutHelper;
import it.deeptelegram.ui.Components.RecyclerListView;
import it.deeptelegram.ui.Components.StickersAlert;

import java.util.ArrayList;
import java.util.HashMap;

public class FeaturedStickersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private LinearLayoutManager layoutManager;

    private ArrayList<Long> unreadStickers = null;
    private HashMap<Long, TLRPC.StickerSetCovered> installingStickerSets = new HashMap<>();

    private int stickersStartRow;
    private int stickersEndRow;
    private int stickersShadowRow;
    private int rowCount;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        StickersQuery.checkFeaturedStickers();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.featuredStickersDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.stickersDidLoaded);
        ArrayList<Long> arrayList = StickersQuery.getUnreadStickerSets();
        if (arrayList != null) {
            unreadStickers = new ArrayList<>(arrayList);
        }
        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.featuredStickersDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.stickersDidLoaded);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("FeaturedStickers", R.string.FeaturedStickers));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(0xfff0f0f0);

        RecyclerListView listView = new RecyclerListView(context);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setFocusable(true);
        listView.setTag(14);
        layoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setLayoutManager(layoutManager);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(final View view, int position) {
                if (position >= stickersStartRow && position < stickersEndRow && getParentActivity() != null) {
                    final TLRPC.StickerSetCovered stickerSet = StickersQuery.getFeaturedStickerSets().get(position);
                    TLRPC.InputStickerSet inputStickerSet;
                    if (stickerSet.set.id != 0) {
                        inputStickerSet = new TLRPC.TL_inputStickerSetID();
                        inputStickerSet.id = stickerSet.set.id;
                    } else {
                        inputStickerSet = new TLRPC.TL_inputStickerSetShortName();
                        inputStickerSet.short_name = stickerSet.set.short_name;
                    }
                    inputStickerSet.access_hash = stickerSet.set.access_hash;
                    StickersAlert stickersAlert = new StickersAlert(getParentActivity(), FeaturedStickersActivity.this, inputStickerSet, null, null);
                    stickersAlert.setInstallDelegate(new StickersAlert.StickersAlertInstallDelegate() {
                        @Override
                        public void onStickerSetInstalled() {
                            FeaturedStickerSetCell cell = (FeaturedStickerSetCell) view;
                            cell.setDrawProgress(true);
                            installingStickerSets.put(stickerSet.set.id, stickerSet);
                        }

                        @Override
                        public void onStickerSetUninstalled() {

                        }
                    });
                    showDialog(stickersAlert);
                }
            }
        });
        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.featuredStickersDidLoaded) {
            if (unreadStickers == null) {
                unreadStickers = StickersQuery.getUnreadStickerSets();
            }
            updateRows();
        } else if (id == NotificationCenter.stickersDidLoaded) {
            updateVisibleTrendingSets();
        }
    }

    private void updateVisibleTrendingSets() {
        if (layoutManager == null) {
            return;
        }
        int first = layoutManager.findFirstVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION) {
            return;
        }
        int last = layoutManager.findLastVisibleItemPosition();
        if (last == RecyclerView.NO_POSITION) {
            return;
        }
        listAdapter.notifyItemRangeChanged(first, last - first + 1);
    }

    private void updateRows() {
        rowCount = 0;
        ArrayList<TLRPC.StickerSetCovered> stickerSets = StickersQuery.getFeaturedStickerSets();
        if (!stickerSets.isEmpty()) {
            stickersStartRow = rowCount;
            stickersEndRow = rowCount + stickerSets.size();
            rowCount += stickerSets.size();
            stickersShadowRow = rowCount++;
        } else {
            stickersStartRow = -1;
            stickersEndRow = -1;
            stickersShadowRow = -1;
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        StickersQuery.markFaturedStickersAsRead(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends RecyclerListView.Adapter {
        private Context mContext;

        private class Holder extends RecyclerView.ViewHolder {

            public Holder(View itemView) {
                super(itemView);
            }
        }

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == 0) {
                ArrayList<TLRPC.StickerSetCovered> arrayList = StickersQuery.getFeaturedStickerSets();
                FeaturedStickerSetCell cell = (FeaturedStickerSetCell) holder.itemView;
                cell.setTag(position);
                TLRPC.StickerSetCovered stickerSet = arrayList.get(position);
                cell.setStickersSet(stickerSet, position != arrayList.size() - 1, unreadStickers != null && unreadStickers.contains(stickerSet.set.id));
                boolean installing = installingStickerSets.containsKey(stickerSet.set.id);
                if (installing && cell.isInstalled()) {
                    installingStickerSets.remove(stickerSet.set.id);
                    installing = false;
                    cell.setDrawProgress(false);
                }
                cell.setDrawProgress(installing);
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new FeaturedStickerSetCell(mContext);
                    view.setBackgroundResource(R.drawable.list_selector_white);
                    ((FeaturedStickerSetCell) view).setAddOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            FeaturedStickerSetCell parent = (FeaturedStickerSetCell) v.getParent();
                            TLRPC.StickerSetCovered pack = parent.getStickerSet();
                            if (installingStickerSets.containsKey(pack.set.id)) {
                                return;
                            }
                            installingStickerSets.put(pack.set.id, pack);
                            StickersQuery.removeStickersSet(getParentActivity(), pack.set, 2, FeaturedStickersActivity.this, false);
                            parent.setDrawProgress(true);
                        }
                    });
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundResource(R.drawable.greydivider_bottom);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new Holder(view);
        }

        @Override
        public int getItemViewType(int i) {
            if (i >= stickersStartRow && i < stickersEndRow) {
                return 0;
            } else if (i == stickersShadowRow) {
                return 1;
            }
            return 0;
        }
    }
}
