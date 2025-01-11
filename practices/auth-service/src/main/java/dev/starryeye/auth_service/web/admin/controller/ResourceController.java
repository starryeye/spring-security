package dev.starryeye.auth_service.web.admin.controller;

import dev.starryeye.auth_service.web.admin.controller.request.CreateResourceRequest;
import dev.starryeye.auth_service.web.admin.facade.usecase.resource.CreateResourceUseCase;
import dev.starryeye.auth_service.web.admin.facade.usecase.resource.DeleteResourceUseCase;
import dev.starryeye.auth_service.web.admin.facade.usecase.resource.GetResourcesUseCase;
import dev.starryeye.auth_service.web.admin.facade.usecase.resource.PrepareResourceRegisterUseCase;
import dev.starryeye.auth_service.web.admin.facade.response.ResourceDetailsResponse;
import dev.starryeye.auth_service.web.admin.facade.response.ResourceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/resources")
public class ResourceController {

    private final PrepareResourceRegisterUseCase prepareResourceRegisterUseCase;
    private final GetResourcesUseCase getResourcesUseCase;
    private final DeleteResourceUseCase deleteResourceUseCase;
    private final CreateResourceUseCase createResourceUseCase;

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

    @PostMapping
    public String createResource(@ModelAttribute CreateResourceRequest request) {

        createResourceUseCase.process(request.toUseCase());

        return "redirect:/admin/resources";
    }

    @GetMapping("/delete/{id}")
    public String deleteResource(@PathVariable("id") Long id) {

        deleteResourceUseCase.by(id);

        return "redirect:/admin/resources";
    }
}
