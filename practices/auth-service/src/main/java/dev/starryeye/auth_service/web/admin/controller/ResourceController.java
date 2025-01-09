package dev.starryeye.auth_service.web.admin.controller;

import dev.starryeye.auth_service.web.admin.facade.usecase.resource.DeleteResourceUseCase;
import dev.starryeye.auth_service.web.admin.facade.usecase.resource.GetResourcesUseCase;
import dev.starryeye.auth_service.web.admin.facade.usecase.resource.PrepareResourceRegisterUseCase;
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
    private final DeleteResourceUseCase deleteResourceUseCase;
    private final PrepareResourceRegisterUseCase prepareResourceRegisterUseCase;

    @GetMapping("/register")
    public String registerResource(Model model) {

        ResourceDetailsResponse resourceDetails = prepareResourceRegisterUseCase.process();

        model.addAttribute("roleList", resourceDetails.allRoles());
        model.addAttribute("myRoles", resourceDetails.roleNamesOfResource());
        model.addAttribute("resources", resourceDetails.resource());

        return "/admin/resourcesdetails";
    }

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

    @GetMapping("/delete/{id}")
    public String deleteResource(@PathVariable("id") Long id) {

        deleteResourceUseCase.process(id);

        return "redirect:/admin/resources";
    }
}
