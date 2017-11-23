/*
 *             Twidere - Twitter client for Android
 *
 *  Copyright (C) 2012-2017 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import com.squareup.otto.Bus
import com.squareup.otto.Subscribe
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import org.mariotaku.abstask.library.TaskStarter
import org.mariotaku.kpreferences.get
import org.mariotaku.ktextension.mapToArray
import org.mariotaku.ktextension.toNulls
import org.mariotaku.microblog.library.MicroBlog
import org.mariotaku.microblog.library.MicroBlogException
import org.mariotaku.microblog.library.twitter.model.FriendshipUpdate
import org.mariotaku.microblog.library.twitter.model.SavedSearch
import org.mariotaku.microblog.library.twitter.model.UserList
import org.mariotaku.microblog.library.twitter.model.UserListUpdate
import org.mariotaku.sqliteqb.library.Expression
import org.mariotaku.twidere.R
import org.mariotaku.twidere.TwidereConstants
import org.mariotaku.twidere.constant.homeRefreshDirectMessagesKey
import org.mariotaku.twidere.constant.homeRefreshMentionsKey
import org.mariotaku.twidere.constant.homeRefreshSavedSearchesKey
import org.mariotaku.twidere.constant.nameFirstKey
import org.mariotaku.twidere.extension.model.api.microblog.toParcelable
import org.mariotaku.twidere.extension.model.newMicroBlogInstance
import org.mariotaku.twidere.extension.promise
import org.mariotaku.twidere.model.*
import org.mariotaku.twidere.model.event.*
import org.mariotaku.twidere.model.pagination.SinceMaxPagination
import org.mariotaku.twidere.model.refresh.ContentRefreshParam
import org.mariotaku.twidere.model.util.AccountUtils
import org.mariotaku.twidere.model.util.ParcelableRelationshipUtils
import org.mariotaku.twidere.provider.TwidereDataStore.*
import org.mariotaku.twidere.task.*
import org.mariotaku.twidere.task.statuses.GetHomeTimelineTask
import org.mariotaku.twidere.task.twitter.GetActivitiesAboutMeTask
import org.mariotaku.twidere.task.twitter.GetSavedSearchesTask
import org.mariotaku.twidere.task.twitter.message.GetMessagesTask
import org.mariotaku.twidere.util.collection.CompactHashSet

class AsyncTwitterWrapper(
        val context: Context,
        private val bus: Bus,
        private val preferences: SharedPreferences,
        private val notificationManager: NotificationManagerWrapper
) {
    private val resolver = context.contentResolver

    private val sendingDraftIds = ArrayList<Long>()

    private val getMessageTasks = CompactHashSet<Uri>()
    private val getStatusTasks = CompactHashSet<Uri>()

    init {
        bus.register(object : Any() {
            @Subscribe
            fun onGetDirectMessagesTaskEvent(event: GetMessagesTaskEvent) {
                if (event.running) {
                    getMessageTasks.add(event.uri)
                } else {
                    getMessageTasks.remove(event.uri)
                }
            }

            @Subscribe
            fun onGetStatusesTaskEvent(event: GetStatusesTaskEvent) {
                if (event.running) {
                    getStatusTasks.add(event.uri)
                } else {
                    getStatusTasks.remove(event.uri)
                }
            }
        })
    }

    fun addUserListMembersAsync(accountKey: UserKey, listId: String, vararg users: ParcelableUser) {
        val task = AddUserListMembersTask(context, accountKey, listId, users)
        TaskStarter.execute(task)
    }

    fun clearNotificationAsync(notificationType: Int) {
        clearNotificationAsync(notificationType, null)
    }

    fun clearNotificationAsync(notificationId: Int, accountKey: UserKey?) {
        notificationManager.cancelById(Utils.getNotificationId(notificationId, accountKey))
    }

    fun createFavoriteAsync(accountKey: UserKey, status: ParcelableStatus) {
        val task = CreateFavoriteTask(context, accountKey, status)
        TaskStarter.execute(task)
    }

    fun createMultiBlockAsync(accountKey: UserKey, userIds: Array<String>) {
    }

    fun createSavedSearchAsync(accountKey: UserKey, query: String) {
        val task = CreateSavedSearchTask(context, accountKey, query)
        TaskStarter.execute(task)
    }

    fun createUserListAsync(accountKey: UserKey, listName: String, isPublic: Boolean,
            description: String) {
        val task = CreateUserListTask(context, accountKey, listName, isPublic,
                description)
        TaskStarter.execute(task)
    }

    fun createUserListSubscriptionAsync(accountKey: UserKey, listId: String) {
        val task = CreateUserListSubscriptionTask(context, accountKey, listId)
        TaskStarter.execute(task)
    }

    fun deleteUserListMembersAsync(accountKey: UserKey, listId: String, users: Array<ParcelableUser>) {
        val task = DeleteUserListMembersTask(context, accountKey, listId, users)
        TaskStarter.execute(task)
    }

    fun destroyFavoriteAsync(accountKey: UserKey, statusId: String) {
        val task = DestroyFavoriteTask(context, accountKey, statusId)
        TaskStarter.execute(task)
    }

    fun destroySavedSearchAsync(accountKey: UserKey, searchId: Long) {
        val task = DestroySavedSearchTask(context, accountKey, searchId)
        TaskStarter.execute(task)
    }

    fun destroyUserListAsync(accountKey: UserKey, listId: String) {
        val task = DestroyUserListTask(context, accountKey, listId)
        TaskStarter.execute(task)
    }

    fun destroyUserListSubscriptionAsync(accountKey: UserKey, listId: String) {
        val task = DestroyUserListSubscriptionTask(context, accountKey, listId)
        TaskStarter.execute(task)
    }

    fun getHomeTimelineAsync(param: ContentRefreshParam): Boolean {
        val task = GetHomeTimelineTask(context)
        task.params = param
        TaskStarter.execute(task)
        return true
    }

    fun getMessagesAsync(param: GetMessagesTask.RefreshMessagesParam) {
        val task = GetMessagesTask(context)
        task.params = param
        TaskStarter.execute(task)
    }

    fun getSavedSearchesAsync(accountKeys: Array<UserKey>): Promise<List<Unit>, Exception> {
        val task = GetSavedSearchesTask(context)
        return task.toPromise(accountKeys)
    }

    fun getSendingDraftIds(): LongArray {
        return sendingDraftIds.toLongArray()
    }

    fun isStatusTimelineRefreshing(uri: Uri): Boolean {
        return getStatusTasks.contains(uri)
    }

    fun refreshAll() {
        refreshAll { DataStoreUtils.getActivatedAccountKeys(context) }
    }

    fun refreshAll(accountKeys: Array<UserKey>): Boolean {
        return refreshAll { accountKeys }
    }

    fun refreshAll(action: () -> Array<UserKey>): Boolean {
        getHomeTimelineAsync(object : ContentRefreshParam {

            override val accountKeys by lazy { action() }

            override val pagination by lazy {
                return@lazy DataStoreUtils.getNewestStatusIds(context, Statuses.HomeTimeline.CONTENT_URI,
                        accountKeys.toNulls()).mapToArray {
                    return@mapToArray SinceMaxPagination.sinceId(it, -1)
                }
            }
        })
        if (preferences[homeRefreshMentionsKey]) {
            getActivitiesAboutMeAsync(object : ContentRefreshParam {

                override val accountKeys by lazy { action() }

                override val pagination by lazy {
                    return@lazy DataStoreUtils.getRefreshNewestActivityMaxPositions(context,
                            Activities.AboutMe.CONTENT_URI, accountKeys.toNulls()).mapToArray {
                        return@mapToArray SinceMaxPagination.sinceId(it, -1)
                    }
                }
            })
        }
        if (preferences[homeRefreshDirectMessagesKey]) {
            getMessagesAsync(object : GetMessagesTask.RefreshMessagesParam(context) {
                override val accountKeys by lazy { action() }
            })
        }
        if (preferences[homeRefreshSavedSearchesKey]) {
            getSavedSearchesAsync(action())
        }
        return true
    }

    fun reportMultiSpam(accountKey: UserKey, userIds: Array<String>) {
        // TODO implementation
    }

    fun retweetStatusAsync(accountKey: UserKey, status: ParcelableStatus) {
        val task = RetweetStatusTask(context, accountKey, status)
        TaskStarter.execute<Any, SingleResponse<ParcelableStatus>, Any>(task)
    }

    fun updateUserListDetails(accountKey: UserKey, listId: String, update: UserListUpdate) {
        val task = UpdateUserListDetailsTask(context, accountKey, listId, update)
        task.promise()
    }

    fun updateFriendship(accountKey: UserKey, userKey: UserKey, update: FriendshipUpdate) {
        TaskStarter.execute(object : AbsAccountRequestTask<Any?, ParcelableRelationship, Any>(context, accountKey) {

            override fun onExecute(account: AccountDetails, params: Any?): ParcelableRelationship {
                val microBlog = account.newMicroBlogInstance(context, MicroBlog::class.java)
                val relationship = microBlog.updateFriendship(userKey.id, update).toParcelable(accountKey, userKey)
                val cr = context.contentResolver
                if (update["retweets"] == false) {
                    val where = Expression.and(
                            Expression.equalsArgs(Statuses.ACCOUNT_KEY),
                            Expression.equalsArgs(Statuses.RETWEETED_BY_USER_KEY)
                    )
                    val selectionArgs = arrayOf(accountKey.toString(), userKey.toString())
                    cr.delete(Statuses.HomeTimeline.CONTENT_URI, where.sql, selectionArgs)
                }

                ParcelableRelationshipUtils.insert(cr, listOf(relationship))
                return relationship
            }

            override fun onSucceed(callback: Any?, result: ParcelableRelationship) {
                bus.post(FriendshipUpdatedEvent(accountKey, userKey, result))
            }

            override fun onException(callback: Any?, exception: MicroBlogException) {
                DebugLog.w(TwidereConstants.LOGTAG, "Unable to update friendship", exception)
            }

        })
    }

    fun getActivitiesAboutMeAsync(param: ContentRefreshParam) {
        val task = GetActivitiesAboutMeTask(context)
        task.params = param
        TaskStarter.execute(task)
    }

    fun setActivitiesAboutMeUnreadAsync(accountKeys: Array<UserKey>, cursor: Long) = task {
        for (accountKey in accountKeys) {
            val microBlog = MicroBlogAPIFactory.getInstance(context, accountKey) ?: continue
            if (!AccountUtils.isOfficial(context, accountKey)) continue
            microBlog.setActivitiesAboutMeUnread(cursor)
        }
    }

    internal class CreateSavedSearchTask(context: Context, accountKey: UserKey,
            private val query: String) : AbsAccountRequestTask<Any?, SavedSearch, Any?>(context,
            accountKey) {

        override fun onExecute(account: AccountDetails, params: Any?): SavedSearch {
            val microBlog = account.newMicroBlogInstance(context, MicroBlog::class.java)
            return microBlog.createSavedSearch(query)
        }

        override fun onSucceed(callback: Any?, result: SavedSearch) {
            val message = context.getString(R.string.message_toast_search_name_saved, result.query)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        override fun onException(callback: Any?, exception: MicroBlogException) {
            if (exception.statusCode == 403) {
                Toast.makeText(context, R.string.saved_searches_already_saved_hint,
                        Toast.LENGTH_SHORT).show()
                return
            }
            super.onException(callback, exception)
        }

    }

    internal class CreateUserListSubscriptionTask(context: Context, accountKey: UserKey,
            private val listId: String) : AbsAccountRequestTask<Any?, ParcelableUserList, Any?>(context, accountKey) {

        override fun onExecute(account: AccountDetails, params: Any?): ParcelableUserList {
            val microBlog = account.newMicroBlogInstance(context, MicroBlog::class.java)
            val userList = microBlog.createUserListSubscription(listId)
            return userList.toParcelable(account.key)
        }

        override fun onSucceed(callback: Any?, result: ParcelableUserList) {
            val message = context.getString(R.string.subscribed_to_list, result.name)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            bus.post(UserListSubscriptionEvent(UserListSubscriptionEvent.Action.SUBSCRIBE, result))
        }

    }

    internal class CreateUserListTask(context: Context, accountKey: UserKey,
            private val listName: String, private val isPublic: Boolean,
            private val description: String) : AbsAccountRequestTask<Any?, ParcelableUserList, Any?>(context, accountKey) {
        override fun onExecute(account: AccountDetails, params: Any?): ParcelableUserList {
            val microBlog = account.newMicroBlogInstance(context, MicroBlog::class.java)
            val userListUpdate = UserListUpdate()
            userListUpdate.setName(listName)
            userListUpdate.setMode(if (isPublic) UserList.Mode.PUBLIC else UserList.Mode.PRIVATE)
            userListUpdate.setDescription(description)
            val list = microBlog.createUserList(userListUpdate)
            return list.toParcelable(account.key)
        }

        override fun onSucceed(callback: Any?, result: ParcelableUserList) {
            val message = context.getString(R.string.created_list, result.name)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            bus.post(UserListCreatedEvent(result))
        }

    }

    internal class DeleteUserListMembersTask(
            context: Context,
            accountKey: UserKey,
            private val userListId: String,
            private val users: Array<ParcelableUser>
    ) : AbsAccountRequestTask<Any?, ParcelableUserList, Any?>(context, accountKey) {

        override fun onExecute(account: AccountDetails, params: Any?): ParcelableUserList {
            val microBlog = account.newMicroBlogInstance(context, MicroBlog::class.java)
            val userKeys = users.mapToArray(ParcelableUser::key)
            val userList = microBlog.deleteUserListMembers(userListId, UserKey.getIds(userKeys))
            return userList.toParcelable(account.key)
        }

        override fun onSucceed(callback: Any?, result: ParcelableUserList) {
            val message = if (users.size == 1) {
                val user = users[0]
                val nameFirst = preferences[nameFirstKey]
                val displayName = userColorNameManager.getDisplayName(user.key,
                        user.name, user.screen_name, nameFirst)
                context.getString(R.string.deleted_user_from_list, displayName,
                        result.name)
            } else {
                context.resources.getQuantityString(R.plurals.deleted_N_users_from_list, users.size,
                        users.size, result.name)
            }
            bus.post(UserListMembersChangedEvent(UserListMembersChangedEvent.Action.REMOVED,
                    result, users))
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

    }


    internal class DestroySavedSearchTask(
            context: Context,
            accountKey: UserKey,
            private val searchId: Long
    ) : AbsAccountRequestTask<Any?, SavedSearch, Any?>(context, accountKey) {

        override fun onExecute(account: AccountDetails, params: Any?): SavedSearch {
            val microBlog = account.newMicroBlogInstance(context, MicroBlog::class.java)
            return microBlog.destroySavedSearch(searchId)
        }

        override fun onSucceed(callback: Any?, result: SavedSearch) {
            val message = context.getString(R.string.message_toast_search_name_deleted, result.query)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            bus.post(SavedSearchDestroyedEvent(accountKey, searchId))
        }

    }

    internal class DestroyUserListSubscriptionTask(
            context: Context,
            accountKey: UserKey,
            private val listId: String
    ) : AbsAccountRequestTask<Any?, ParcelableUserList, Any?>(context, accountKey) {

        override fun onExecute(account: AccountDetails, params: Any?): ParcelableUserList {
            val microBlog = account.newMicroBlogInstance(context, MicroBlog::class.java)
            val userList = microBlog.destroyUserListSubscription(listId)
            return userList.toParcelable(account.key)
        }

        override fun onSucceed(callback: Any?, result: ParcelableUserList) {
            val message = context.getString(R.string.unsubscribed_from_list, result.name)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            bus.post(UserListSubscriptionEvent(UserListSubscriptionEvent.Action.UNSUBSCRIBE, result))
        }

    }


}
