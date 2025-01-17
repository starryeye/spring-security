package dev.starryeye.auth_service.web.admin.controller;

import dev.starryeye.auth_service.web.admin.controller.request.CreateRoleRequest;
import dev.starryeye.auth_service.web.admin.controller.request.UpdateRoleRequest;
import dev.starryeye.auth_service.web.admin.facade.response.RoleResponse;
import dev.starryeye.auth_service.web.admin.facade.usecase.role.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/roles")
public class RoleController {

    private final PrepareRoleRegisterUseCase prepareRoleRegisterUseCase;
    private final GetRolesUseCase getRolesUseCase;
    private final DeleteRoleUseCase deleteRoleUseCase;
    private final CreateRoleUseCase createRoleUseCase;
    private final ModifyRoleUseCase modifyRoleUseCase;

    @GetMapping("/register")
    public String registerRole(Model model) {
        RoleResponse roleResponse = prepareRoleRegisterUseCase.process();
        model.addAttribute("roles", roleResponse);
        return "/admin/rolesdetails";
    }

    @GetMapping
    public String getAllRoles(Model model) {

        List<RoleResponse> roleResponses = getRolesUseCase.getRoles();
        model.addAttribute("roles", roleResponses);

        return "/admin/roles";
    }

    @GetMapping("/{id}")
    public String getRole(@PathVariable("id") Long id, Model model) {

        RoleResponse roleResponse = getRolesUseCase.getRoleBy(id);
        model.addAttribute("roles", roleResponse);

        return "/admin/rolesdetails";
    }

    @PostMapping
    public String createRole(@ModelAttribute CreateRoleRequest request) {

        createRoleUseCase.process(request.toUseCase());

        return "redirect:/admin/roles";
    }

    @PostMapping("/update")
    public String updateRole(@ModelAttribute UpdateRoleRequest request) {

        modifyRoleUseCase.process(request.toUseCase());

        return "redirect:/admin/roles";
    }

    @GetMapping("/delete/{id}")
    public String deleteRole(@PathVariable("id") Long id) {

        deleteRoleUseCase.by(id);

        return "redirect:/admin/roles";
    }

}
