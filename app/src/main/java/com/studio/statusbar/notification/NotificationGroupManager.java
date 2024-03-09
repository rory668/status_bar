package com.studio.statusbar.notification;

import android.service.notification.StatusBarNotification;
import androidx.annotation.Nullable;

import com.studio.statusbar.notification.ExpandableNotificationRow;
import com.studio.statusbar.notification.NotificationData;
import com.studio.statusbar.panel.StatusBarState;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * A class to handle notifications and their corresponding groups.
 */
public class NotificationGroupManager {

    private final HashMap<String, NotificationGroup> mGroupMap = new HashMap<>();
    private OnGroupChangeListener mListener;
    private int mBarState = -1;
    private HashMap<String, StatusBarNotification> mIsolatedEntries = new HashMap<>();

    public void setOnGroupChangeListener(OnGroupChangeListener listener) {
        mListener = listener;
    }

    public boolean isGroupExpanded(StatusBarNotification sbn) {
        NotificationGroup group = mGroupMap.get(getGroupKey(sbn));
        if (group == null) {
            return false;
        }
        return group.expanded;
    }

    public void setGroupExpanded(StatusBarNotification sbn, boolean expanded) {
        NotificationGroup group = mGroupMap.get(getGroupKey(sbn));
        if (group == null) {
            return;
        }
        setGroupExpanded(group, expanded);
    }

    private void setGroupExpanded(NotificationGroup group, boolean expanded) {
        group.expanded = expanded;
        if (group.summary != null) {
            mListener.onGroupExpansionChanged(group.summary.row, expanded);
        }
    }

    public void onEntryRemoved(NotificationData.Entry removed) {
        onEntryRemovedInternal(removed, removed.notification);
        mIsolatedEntries.remove(removed.key);
    }

    /**
     * An entry was removed.
     *
     * @param removed the removed entry
     * @param sbn the notification the entry has, which doesn't need to be the same as it's internal
     *            notification
     */
    private void onEntryRemovedInternal(NotificationData.Entry removed,
            final StatusBarNotification sbn) {
        String groupKey = getGroupKey(sbn);
        final NotificationGroup group = mGroupMap.get(groupKey);
        if (group == null) {
            // When an app posts 2 different notifications as summary of the same group, then a
            // cancellation of the first notification removes this group.
            // This situation is not supported and we will not allow such notifications anymore in
            // the close future. See b/23676310 for reference.
            return;
        }
        if (isGroupChild(sbn)) {
            group.children.remove(removed);
        } else {
            group.summary = null;
        }
        updateSuppression(group);
        if (group.children.isEmpty()) {
            if (group.summary == null) {
                mGroupMap.remove(groupKey);
            }
        }
    }

    public void onEntryAdded(final NotificationData.Entry added) {
        final StatusBarNotification sbn = added.notification;
        boolean isGroupChild = isGroupChild(sbn);
        String groupKey = getGroupKey(sbn);
        NotificationGroup group = mGroupMap.get(groupKey);
        if (group == null) {
            group = new NotificationGroup();
            mGroupMap.put(groupKey, group);
        }
        if (isGroupChild) {
            group.children.add(added);
            updateSuppression(group);
        } else {
            group.summary = added;
            group.expanded = added.row.areChildrenExpanded();
            updateSuppression(group);
            if (!group.children.isEmpty()) {
                HashSet<NotificationData.Entry> childrenCopy =
                        (HashSet<NotificationData.Entry>) group.children.clone();
                for (NotificationData.Entry child : childrenCopy) {
                    onEntryBecomingChild(child);
                }
                mListener.onGroupCreatedFromChildren(group);
            }
        }
    }

    private void onEntryBecomingChild(NotificationData.Entry entry) {
    }

    public void onEntryBundlingUpdated(final NotificationData.Entry updated,
            final String overrideGroupKey) {
        final StatusBarNotification oldSbn = updated.notification.clone();
        if (!Objects.equals(oldSbn.getOverrideGroupKey(), overrideGroupKey)) {
            updated.notification.setOverrideGroupKey(overrideGroupKey);
            onEntryUpdated(updated, oldSbn);
        }
    }

    private void updateSuppression(NotificationGroup group) {
        if (group == null) {
            return;
        }
        boolean prevSuppressed = group.suppressed;
        group.suppressed = group.summary != null && !group.expanded
                && (group.children.size() == 1
                || (group.children.size() == 0
                        && group.summary.notification.getNotification().isGroupSummary()
                        && hasIsolatedChildren(group)));
        if (prevSuppressed != group.suppressed) {
            mListener.onGroupsChanged();
        }
    }

    private boolean hasIsolatedChildren(NotificationGroup group) {
        return getNumberOfIsolatedChildren(group.summary.notification.getGroupKey()) != 0;
    }

    private int getNumberOfIsolatedChildren(String groupKey) {
        int count = 0;
        for (StatusBarNotification sbn : mIsolatedEntries.values()) {
            if (sbn.getGroupKey().equals(groupKey) && isIsolated(sbn)) {
                count++;
            }
        }
        return count;
    }

    private NotificationData.Entry getIsolatedChild(String groupKey) {
        for (StatusBarNotification sbn : mIsolatedEntries.values()) {
            if (sbn.getGroupKey().equals(groupKey) && isIsolated(sbn)) {
                return mGroupMap.get(sbn.getKey()).summary;
            }
        }
        return null;
    }

    public void onEntryUpdated(NotificationData.Entry entry,
            StatusBarNotification oldNotification) {
        if (mGroupMap.get(getGroupKey(oldNotification)) != null) {
            onEntryRemovedInternal(entry, oldNotification);
        }
        onEntryAdded(entry);
        if (isIsolated(entry.notification)) {
            mIsolatedEntries.put(entry.key, entry.notification);
            String oldKey = oldNotification.getGroupKey();
            String newKey = entry.notification.getGroupKey();
            if (!oldKey.equals(newKey)) {
                updateSuppression(mGroupMap.get(oldKey));
                updateSuppression(mGroupMap.get(newKey));
            }
        } else if (!isGroupChild(oldNotification) && isGroupChild(entry.notification)) {
            onEntryBecomingChild(entry);
        }
    }

    public boolean isSummaryOfSuppressedGroup(StatusBarNotification sbn) {
        return isGroupSuppressed(getGroupKey(sbn)) && sbn.getNotification().isGroupSummary();
    }

    private boolean isOnlyChild(StatusBarNotification sbn) {
        return !sbn.getNotification().isGroupSummary()
                && getTotalNumberOfChildren(sbn) == 1;
    }

    public boolean isOnlyChildInGroup(StatusBarNotification sbn) {
        if (!isOnlyChild(sbn)) {
            return false;
        }
        ExpandableNotificationRow logicalGroupSummary = getLogicalGroupSummary(sbn);
        return logicalGroupSummary != null
                && !logicalGroupSummary.getStatusBarNotification().equals(sbn);
    }

    private int getTotalNumberOfChildren(StatusBarNotification sbn) {
        int isolatedChildren = getNumberOfIsolatedChildren(sbn.getGroupKey());
        NotificationGroup group = mGroupMap.get(sbn.getGroupKey());
        int realChildren = group != null ? group.children.size() : 0;
        return isolatedChildren + realChildren;
    }

    private boolean isGroupSuppressed(String groupKey) {
        NotificationGroup group = mGroupMap.get(groupKey);
        return group != null && group.suppressed;
    }

    public void setStatusBarState(int newState) {
        if (mBarState == newState) {
            return;
        }
        mBarState = newState;
        if (mBarState == StatusBarState.KEYGUARD) {
            collapseAllGroups();
        }
    }

    public void collapseAllGroups() {
        // Because notifications can become isolated when the group becomes suppressed it can
        // lead to concurrent modifications while looping. We need to make a copy.
        ArrayList<NotificationGroup> groupCopy = new ArrayList<>(mGroupMap.values());
        int size = groupCopy.size();
        for (int i = 0; i < size; i++) {
            NotificationGroup group =  groupCopy.get(i);
            if (group.expanded) {
                setGroupExpanded(group, false);
            }
            updateSuppression(group);
        }
    }

    /**
     * @return whether a given notification is a child in a group which has a summary
     */
    public boolean isChildInGroupWithSummary(StatusBarNotification sbn) {
        if (!isGroupChild(sbn)) {
            return false;
        }
        NotificationGroup group = mGroupMap.get(getGroupKey(sbn));
        if (group == null || group.summary == null || group.suppressed) {
            return false;
        }
        if (group.children.isEmpty()) {
            // If the suppression of a group changes because the last child was removed, this can
            // still be called temporarily because the child hasn't been fully removed yet. Let's
            // make sure we still return false in that case.
            return false;
        }
        return true;
    }

    /**
     * @return whether a given notification is a summary in a group which has children
     */
    public boolean isSummaryOfGroup(StatusBarNotification sbn) {
        if (!isGroupSummary(sbn)) {
            return false;
        }
        NotificationGroup group = mGroupMap.get(getGroupKey(sbn));
        if (group == null) {
            return false;
        }
        return !group.children.isEmpty();
    }

    /**
     * Get the summary of a specified status bar notification. For isolated notification this return
     * itself.
     */
    public ExpandableNotificationRow getGroupSummary(StatusBarNotification sbn) {
        return getGroupSummary(getGroupKey(sbn));
    }

    /**
     * Similar to {@link #getGroupSummary(StatusBarNotification)} but doesn't get the visual summary
     * but the logical summary, i.e when a child is isolated, it still returns the summary as if
     * it wasn't isolated.
     */
    public ExpandableNotificationRow getLogicalGroupSummary(
            StatusBarNotification sbn) {
        return getGroupSummary(sbn.getGroupKey());
    }

    @Nullable
    private ExpandableNotificationRow getGroupSummary(String groupKey) {
        NotificationGroup group = mGroupMap.get(groupKey);
        return group == null ? null
                : group.summary == null ? null
                        : group.summary.row;
    }

    /** @return group expansion state after toggling. */
    public boolean toggleGroupExpansion(StatusBarNotification sbn) {
        NotificationGroup group = mGroupMap.get(getGroupKey(sbn));
        if (group == null) {
            return false;
        }
        setGroupExpanded(group, !group.expanded);
        return group.expanded;
    }

    private boolean isIsolated(StatusBarNotification sbn) {
        return mIsolatedEntries.containsKey(sbn.getKey());
    }

    private boolean isGroupSummary(StatusBarNotification sbn) {
        if (isIsolated(sbn)) {
            return true;
        }
        return sbn.getNotification().isGroupSummary();
    }

    private boolean isGroupChild(StatusBarNotification sbn) {
        if (isIsolated(sbn)) {
            return false;
        }
        return sbn.isGroup() && !sbn.getNotification().isGroupSummary();
    }

    private String getGroupKey(StatusBarNotification sbn) {
        if (isIsolated(sbn)) {
            return sbn.getKey();
        }
        return sbn.getGroupKey();
    }

    private boolean shouldIsolate(StatusBarNotification sbn) {
        NotificationGroup notificationGroup = mGroupMap.get(sbn.getGroupKey());
        return (sbn.isGroup() && !sbn.getNotification().isGroupSummary())
                && (sbn.getNotification().fullScreenIntent != null
                        || notificationGroup == null
                        || !notificationGroup.expanded
                        || isGroupNotFullyVisible(notificationGroup));
    }

    private boolean isGroupNotFullyVisible(NotificationGroup notificationGroup) {
        return notificationGroup.summary == null
                || notificationGroup.summary.row.getClipTopAmount() > 0
                || notificationGroup.summary.row.getTranslationY() < 0;
    }

    public static class NotificationGroup {
        public final HashSet<NotificationData.Entry> children = new HashSet<>();
        public NotificationData.Entry summary;
        public boolean expanded;
        /**
         * Is this notification group suppressed, i.e its summary is hidden
         */
        public boolean suppressed;

        @Override
        public String toString() {
            String result = "    summary:\n      "
                    + (summary != null ? summary.notification : "null");
            result += "\n    children size: " + children.size();
            for (NotificationData.Entry child : children) {
                result += "\n      " + child.notification;
            }
            return result;
        }
    }

    public interface OnGroupChangeListener {
        /**
         * The expansion of a group has changed.
         *
         * @param changedRow the row for which the expansion has changed, which is also the summary
         * @param expanded a boolean indicating the new expanded state
         */
        void onGroupExpansionChanged(ExpandableNotificationRow changedRow, boolean expanded);

        /**
         * A group of children just received a summary notification and should therefore become
         * children of it.
         *
         * @param group the group created
         */
        void onGroupCreatedFromChildren(NotificationGroup group);

        /**
         * The groups have changed. This can happen if the isolation of a child has changes or if a
         * group became suppressed / unsuppressed
         */
        void onGroupsChanged();
    }
  }
