package com.philia.projectservice.files.internal.adapter.in.web.dto.request;

import com.philia.projectservice.files.api.ApplyFileChangesCommand;
import com.philia.projectservice.files.api.FilesResults;
import java.util.List;

/** JSON payload only; project ID, expected revision and lease ID come from the URL/headers. */
public record ApplyFileChangesRequest(List<ApplyFileChangesCommand.Change> changes,
                                      String label, FilesResults.Source source) {}
