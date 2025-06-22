/*
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

package com.facebook.presto.router.scheduler;

import com.facebook.airlift.stats.CounterStat;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

public class RequestStats
{
    private final CounterStat javaClusterRedirectRequests = new CounterStat();
    private final CounterStat nativeClusterRedirectRequests = new CounterStat();
    private static final RequestStats INSTANCE = new RequestStats();

    @Managed
    @Nested
    public CounterStat getJavaClusterRedirectRequests()
    {
        return javaClusterRedirectRequests;
    }

    @Managed
    @Nested
    public CounterStat getNativeClusterRedirectRequests()
    {
        return nativeClusterRedirectRequests;
    }

    public void updateJavaRequests(long count)
    {
        javaClusterRedirectRequests.update(count);
    }

    public void updateNativeRequests(long count)
    {
        nativeClusterRedirectRequests.update(count);
    }

    public static final RequestStats getInstance()
    {
        return INSTANCE;
    }
}
