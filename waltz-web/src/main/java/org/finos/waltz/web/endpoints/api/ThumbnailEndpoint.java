/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
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
 * See the License for the specific
 *
 */

package org.finos.waltz.web.endpoints.api;

import org.finos.waltz.service.thumbnail.ThumbnailService;
import org.finos.waltz.service.user.UserRoleService;
import org.finos.waltz.web.WebUtilities;
import org.finos.waltz.web.endpoints.Endpoint;
import org.finos.waltz.model.EntityReference;
import org.finos.waltz.model.Operation;
import org.finos.waltz.model.thumbnail.ThumbnailSaveCommand;
import org.finos.waltz.web.endpoints.EndpointUtilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import spark.Request;

import java.io.IOException;

import static org.finos.waltz.common.Checks.checkNotNull;
import static org.finos.waltz.web.WebUtilities.requireEditRoleForEntity;

@Service
public class ThumbnailEndpoint implements Endpoint {

    private final String BASE_URL = WebUtilities.mkPath("api", "thumbnail");
    private final ThumbnailService thumbnailService;
    private final UserRoleService userRoleService;


    @Autowired
    public ThumbnailEndpoint(ThumbnailService thumbnailService,
                             UserRoleService userRoleService) {
        checkNotNull(thumbnailService, "thumbnailService cannot be null");
        checkNotNull(userRoleService, "userRoleService cannot be null");
        this.thumbnailService = thumbnailService;
        this.userRoleService = userRoleService;
    }


    @Override
    public void register() {
        String byRefPath = WebUtilities.mkPath(BASE_URL, ":kind", ":id");
        String savePath = WebUtilities.mkPath(BASE_URL, "save");


        EndpointUtilities.getForDatum(byRefPath, (req, res) -> {
            EntityReference entityRef = WebUtilities.getEntityReference(req);
            return thumbnailService.getByReference(entityRef);
        });


        EndpointUtilities.deleteForDatum(byRefPath, (req, res) -> delete(req));

        EndpointUtilities.postForDatum(savePath, (req, res) -> save(req));
    }


    boolean delete(Request req) {
        EntityReference entityRef = WebUtilities.getEntityReference(req);
        checkHasPermission(req, entityRef, Operation.REMOVE);
        return thumbnailService.deleteByReference(entityRef, WebUtilities.getUsername(req));
    }


    boolean save(Request req) throws IOException {
        return save(req, WebUtilities.readBody(req, ThumbnailSaveCommand.class));
    }


    boolean save(Request req, ThumbnailSaveCommand cmd) {
        checkHasPermission(req, cmd.parentEntityReference(), Operation.UPDATE);
        thumbnailService.save(cmd, WebUtilities.getUsername(req));
        return true;
    }


    private void checkHasPermission(Request req,
                                    EntityReference ref,
                                    Operation operation) {
        requireEditRoleForEntity(
                userRoleService,
                req,
                ref.kind(),
                operation,
                null);
    }
}
