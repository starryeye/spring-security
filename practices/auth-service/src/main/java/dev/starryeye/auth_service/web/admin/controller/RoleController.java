package dev.starryeye.auth_service.web.admin.controller;

import dev.starryeye.auth_service.web.admin.facade.usecase.role.DeleteRoleUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/roles")
public class RoleController {

    private final DeleteRoleUseCase deleteRoleUseCase;

    @GetMapping("/delete/{id}")
    public String deleteRole(@PathVariable("id") Long id) {

        deleteRoleUseCase.process(id);
        
        return "redirect:/admin/roles";
    }
}
