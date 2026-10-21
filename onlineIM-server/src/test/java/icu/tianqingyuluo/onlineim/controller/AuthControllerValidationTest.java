package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.exception.GlobalExceptionHandler;
import icu.tianqingyuluo.onlineim.pojo.entity.User;
import icu.tianqingyuluo.onlineim.service.JwtService;
import icu.tianqingyuluo.onlineim.service.UserService;
import icu.tianqingyuluo.onlineim.service.impl.UserDetailsServiceImpl;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerValidationTest {

    private UserService userService;
    private org.springframework.security.authentication.AuthenticationManager authenticationManager;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        authenticationManager = mock(org.springframework.security.authentication.AuthenticationManager.class);
        AuthController controller = new AuthController(
                authenticationManager,
                mock(UserDetailsServiceImpl.class),
                userService,
                mock(JwtUtil.class),
                mock(JwtService.class));
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void registerOmitsPasswordAndStoresHash() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"abcd\",\"password\":\"secret12\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("abcd"))
                .andExpect(jsonPath("$.user_id").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.nickname").exists());

        verify(userService).registerUser(argThat((User user) ->
                user.getPassword() != null
                        && !user.getPassword().equals("secret12")
                        && user.getPassword().startsWith("$2")));
    }

    @Test
    void registerRejectsInvalidUsernameWith400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"password\":\"secret12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").exists());

        verifyNoInteractions(userService);
    }

    @Test
    void loginRejectsBlankCredentialsWith400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").exists());

        verifyNoInteractions(authenticationManager);
    }
}
