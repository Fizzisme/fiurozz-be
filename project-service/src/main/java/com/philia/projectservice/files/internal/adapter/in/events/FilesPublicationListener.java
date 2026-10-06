package com.philia.projectservice.files.internal.adapter.in.events;

import com.philia.projectservice.catalog.api.ProjectPublishedEvent;
import com.philia.projectservice.files.internal.application.ProjectFilesHandler;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Synchronous listener: a pin failure rolls catalog publication back as well. */
@Component
public class FilesPublicationListener {
    private final ProjectFilesHandler handler;
    public FilesPublicationListener(ProjectFilesHandler handler) { this.handler = handler; }
    @EventListener
    public void onPublished(ProjectPublishedEvent event) { handler.pinPublished(event); }
}
