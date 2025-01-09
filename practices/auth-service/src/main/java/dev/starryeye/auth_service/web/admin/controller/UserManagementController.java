package dev.starryeye.auth_service.web.admin.controller;

import dev.starryeye.auth_service.web.admin.facade.response.UserManagementResponse;
import dev.starryeye.auth_service.web.admin.facade.response.UserResponse;
import dev.starryeye.auth_service.web.admin.facade.usecase.usermanagement.DeleteUserUseCase;
import dev.starryeye.auth_service.web.admin.facade.usecase.usermanagement.GetUserManagementUseCase;
import dev.starryeye.auth_service.web.admin.facade.usecase.usermanagement.GetUsersUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/users")
public class UserManagementController {

    private final GetUsersUseCase getUsersUseCase;
    private final GetUserManagementUseCase getUserManagementUseCase;

    private final DeleteUserUseCase deleteUserUseCase;

    @GetMapping
    public String getUsers(Model model) {

        List<UserResponse> userResponses = getUsersUseCase.getUsers();
        model.addAttribute("users", userResponses);

        return "/admin/users";
    }

    @GetMapping("/{id}")
    public String getUserManagement(@PathVariable("id") Long id, Model model) {

        UserManagementResponse userManagementResponse = getUserManagementUseCase.getUserManagementBy(id);
        model.addAttribute("user", userManagementResponse.userResponse());
        model.addAttribute("roleList", userManagementResponse.roles());

        return "/admin/userdetails";
    }

    @GetMapping("/delete/{id}")
    public String deleteUser(@PathVariable("id") Long id) {

        deleteUserUseCase.by(id);

        return "redirect:/admin/users";
    }
}
