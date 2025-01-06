package dev.starryeye.auth_service.web.admin.controller;

import dev.starryeye.auth_service.web.admin.facade.GetResourcesUseCase;
import dev.starryeye.auth_service.web.admin.facade.response.ResourceDetailsResponse;
import dev.starryeye.auth_service.web.admin.facade.response.ResourceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/resources")
public class ResourceController {

    private final GetResourcesUseCase getResourcesUseCase;

    @GetMapping
    public String getResources(Model model) {

        List<ResourceResponse> resources = getResourcesUseCase.getResources();
        model.addAttribute("resources", resources);

        return "/admin/resources";
    }

    @GetMapping("/{id}")
    public String getResource(Model model, @PathVariable("id") Long id) {

        ResourceDetailsResponse resourceDetails = getResourcesUseCase.getResourceBy(id);

        model.addAttribute("roleList", resourceDetails.allRoles());
        model.addAttribute("myRoles", resourceDetails.roleNamesOfResource());
        model.addAttribute("resources", resourceDetails.resource());

        return "/admin/resourcesdetails";
    }
}
