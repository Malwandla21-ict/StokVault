package com.stokvault.web;

import com.stokvault.dto.GroupView;
import com.stokvault.dto.MyPosition;
import com.stokvault.service.GroupService;
import com.stokvault.service.ReportService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.List;

/**
 * "My stokvels" (SDD 6.1 Member dashboard): my position in each group, plus every group I can see.
 */
@Named
@ViewScoped
public class DashboardBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private ReportService reports;

    @Inject
    private GroupService groups;

    private List<MyPosition> positions;
    private List<GroupView> allGroups;

    // @PostConstruct: runs once when the bean is created, after injection
    @PostConstruct
    void load() {
        positions = reports.myPositions();
        allGroups = groups.list();
    }

    public List<MyPosition> getPositions() {
        return positions;
    }

    public List<GroupView> getAllGroups() {
        return allGroups;
    }
}
