package dev.starryeye.auth_service.web.admin.controller;

import dev.starryeye.auth_service.web.admin.facade.response.UserManagementResponse;
import dev.starryeye.auth_service.web.admin.facade.usecase.usermanagement.GetUserManagementUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/users")
public class UserManagementController {

    private final GetUserManagementUseCase getUserManagementUseCase;

    @GetMapping("/{id}")
    public String getUserManagement(@PathVariable("id") Long id, Model model) {

        UserManagementResponse userManagementResponse = getUserManagementUseCase.getUserManagementBy(id);
        model.addAttribute("user", userManagementResponse.userResponse());
        model.addAttribute("roleList", userManagementResponse.roles());

        return "/admin/userdetails";
    }
}
