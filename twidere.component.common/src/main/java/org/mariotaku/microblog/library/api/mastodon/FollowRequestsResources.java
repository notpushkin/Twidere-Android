/*
 *         Twidere - Twitter client for Android
 *
 * Copyright 2012-2017 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mariotaku.microblog.library.api.mastodon;

import org.mariotaku.microblog.library.MicroBlogException;
import org.mariotaku.microblog.library.model.mastodon.Account;
import org.mariotaku.microblog.library.model.mastodon.LinkHeaderList;
import org.mariotaku.microblog.library.model.microblog.Paging;
import org.mariotaku.microblog.library.model.microblog.ResponseCode;
import org.mariotaku.restfu.annotation.method.GET;
import org.mariotaku.restfu.annotation.method.POST;
import org.mariotaku.restfu.annotation.param.Path;
import org.mariotaku.restfu.annotation.param.Query;

/**
 * Created by mariotaku on 2017/4/17.
 */
public interface FollowRequestsResources {
    @GET("/v1/follow_requests")
    LinkHeaderList<Account> getFollowRequests(@Query Paging paging) throws MicroBlogException;

    @POST("/v1/follow_requests/{id}/authorize")
    ResponseCode authorizeFollowRequest(@Path("id") String id) throws MicroBlogException;

    @POST("/v1/follow_requests/{id}/reject")
    ResponseCode rejectFollowRequest(@Path("id") String id) throws MicroBlogException;

}