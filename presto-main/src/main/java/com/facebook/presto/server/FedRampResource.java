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
package com.facebook.presto.server;

import com.facebook.airlift.http.server.AuthenticationException;
import com.facebook.airlift.log.Logger;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;

import java.io.IOException;

import static com.facebook.presto.server.security.RoleType.ADMIN;
import static com.facebook.presto.server.security.RoleType.USER;
import static com.google.common.net.HttpHeaders.WWW_AUTHENTICATE;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

@Path("/")
@RolesAllowed({ADMIN, USER})
public class FedRampResource
{
    Logger log = Logger.get(FedRampResource.class);

    @GET
    @Path("/v1/logout")
    public void logout(@Context HttpServletResponse response)
            throws AuthenticationException, IOException
    {
        log.debug("Logging out user");
        response.setHeader(WWW_AUTHENTICATE, "Basic realm=\"Presto\"");
        response.sendError(SC_UNAUTHORIZED);
        response.setContentType("text/plain");
        response.getWriter().write("");
        response.getWriter().flush();
    }
}
