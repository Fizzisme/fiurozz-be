package com.philia.projectservice.catalog.api;

public interface ChangeProjectVisibilityUseCase {

    ProjectDetailResult changeVisibility(ChangeProjectVisibilityCommand command);
}
