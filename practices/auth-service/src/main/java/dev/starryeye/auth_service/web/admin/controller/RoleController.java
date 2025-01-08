package dev.starryeye.auth_service.web.admin.controller;

import dev.starryeye.auth_service.web.admin.facade.response.RoleResponse;
import dev.starryeye.auth_service.web.admin.facade.usecase.role.DeleteRoleUseCase;
import dev.starryeye.auth_service.web.admin.facade.usecase.role.GetRolesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/roles")
public class RoleController {

    private final GetRolesUseCase getRolesUseCase;
    private final DeleteRoleUseCase deleteRoleUseCase;

    @GetMapping
    public String getAllRoles(Model model) {

        List<RoleResponse> roleResponses = getRolesUseCase.process();
        model.addAttribute("roles", roleResponses);

        return "/admin/roles";
    }


    @GetMapping("/delete/{id}")
    public String deleteRole(@PathVariable("id") Long id) {
                deleteRoleUseCase.process(id);


        return "redirect:/admin/roles";
    }

}
