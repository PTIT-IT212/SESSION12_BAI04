package com.re.ss12sroucecode;

import com.re.ss12sroucecode.enums.Gender;
import com.re.ss12sroucecode.enums.IdentityMethod;
import com.re.ss12sroucecode.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class AccountControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        accountRepository.deleteAll();
    }

    @Test
    void testModule1_Register_Success() throws Exception {
        String requestJson = "{\"phone\":\"0987654321\",\"email\":\"test@abcbank.com.vn\"}";

        mockMvc.perform(post("/api/v1/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone", is("0987654321")))
                .andExpect(jsonPath("$.email", is("test@abcbank.com.vn")))
                .andExpect(jsonPath("$.status", is("INITIATED")))
                .andExpect(jsonPath("$.message", containsString("Mã OTP đã được gửi")));
    }

    @Test
    void testModule1_Register_InvalidInput() throws Exception {
        String requestJson = "{\"phone\":\"1234567\",\"email\":\"invalid-email\"}";

        mockMvc.perform(post("/api/v1/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.details.phone", notNullValue()))
                .andExpect(jsonPath("$.details.email", notNullValue()));
    }

    @Test
    void testModule1_VerifyOtp_Success() throws Exception {
        String phone = "0912345123";
        String registerJson = String.format("{\"phone\":\"%s\",\"email\":\"otp-success@abcbank.com.vn\"}", phone);

        mockMvc.perform(post("/api/v1/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        String verifyJson = String.format("{\"phone\":\"%s\",\"otpCode\":\"123456\"}", phone);

        mockMvc.perform(post("/api/v1/accounts/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("OTP_VERIFIED")))
                .andExpect(jsonPath("$.message", containsString("Xác thực số điện thoại thành công")));
    }

    @Test
    void testModule1_VerifyOtp_Incorrect_Lockout() throws Exception {
        String phone = "0987654123";
        String registerJson = String.format("{\"phone\":\"%s\",\"email\":\"otp-lockout@abcbank.com.vn\"}", phone);

        mockMvc.perform(post("/api/v1/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        String verifyJson = String.format("{\"phone\":\"%s\",\"otpCode\":\"999999\"}", phone);

        // Lần 1 sai
        mockMvc.perform(post("/api/v1/accounts/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Mã OTP không chính xác. Số lần thử còn lại: 2/3")));

        // Lần 2 sai
        mockMvc.perform(post("/api/v1/accounts/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Mã OTP không chính xác. Số lần thử còn lại: 1/3")));

        // Lần 3 sai -> Bị khóa 24h
        mockMvc.perform(post("/api/v1/accounts/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Số điện thoại đăng ký này bị khóa trong 24 giờ")));
    }

    @Test
    void testModule2_VerifyIdentity_Success() throws Exception {
        String phone = "0981234123";
        String registerJson = String.format("{\"phone\":\"%s\",\"email\":\"identity@abcbank.com.vn\"}", phone);
        mockMvc.perform(post("/api/v1/accounts/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson));

        String verifyOtpJson = String.format("{\"phone\":\"%s\",\"otpCode\":\"123456\"}", phone);
        mockMvc.perform(post("/api/v1/accounts/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyOtpJson));

        String identityJson = String.format(
                "{\"phone\":\"%s\",\"citizenId\":\"012345678901\",\"fullName\":\"TRẦN ĐỨC ANH\"," +
                "\"dateOfBirth\":\"2000-01-01\",\"gender\":\"MALE\",\"dateOfExpiry\":\"%s\"," +
                "\"identityMethod\":\"NFC\"}",
                phone, LocalDate.now().plusYears(10));

        mockMvc.perform(post("/api/v1/accounts/verify-identity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identityJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IDENTITY_VERIFIED")))
                .andExpect(jsonPath("$.fullName", is("TRẦN ĐỨC ANH")))
                .andExpect(jsonPath("$.identityMethod", is("NFC")));
    }

    @Test
    void testModule2_VerifyIdentity_CAInvalid() throws Exception {
        String phone = "0982345123";
        String registerJson = String.format("{\"phone\":\"%s\",\"email\":\"cainvalid@abcbank.com.vn\"}", phone);
        mockMvc.perform(post("/api/v1/accounts/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson));

        String verifyOtpJson = String.format("{\"phone\":\"%s\",\"otpCode\":\"123456\"}", phone);
        mockMvc.perform(post("/api/v1/accounts/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyOtpJson));

        String identityJson = String.format(
                "{\"phone\":\"%s\",\"citizenId\":\"999999999999\",\"fullName\":\"KẺ GIAN LẬN\"," +
                "\"dateOfBirth\":\"1990-05-05\",\"gender\":\"FEMALE\",\"dateOfExpiry\":\"%s\"," +
                "\"identityMethod\":\"NFC\"}",
                phone, LocalDate.now().plusYears(5));

        mockMvc.perform(post("/api/v1/accounts/verify-identity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identityJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("CA INVALID")));
    }

    @Test
    void testModule3And4_VerifyBiometric_Approved() throws Exception {
        String phone = "0983456123";
        String registerJson = String.format("{\"phone\":\"%s\",\"email\":\"success@abcbank.com.vn\"}", phone);
        mockMvc.perform(post("/api/v1/accounts/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson));

        String verifyOtpJson = String.format("{\"phone\":\"%s\",\"otpCode\":\"123456\"}", phone);
        mockMvc.perform(post("/api/v1/accounts/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyOtpJson));

        String identityJson = String.format(
                "{\"phone\":\"%s\",\"citizenId\":\"036200123456\",\"fullName\":\"NGUYỄN VĂN A\"," +
                "\"dateOfBirth\":\"1995-12-25\",\"gender\":\"MALE\",\"dateOfExpiry\":\"%s\"," +
                "\"identityMethod\":\"NFC\"}",
                phone, LocalDate.now().plusYears(10));
        mockMvc.perform(post("/api/v1/accounts/verify-identity")
                .contentType(MediaType.APPLICATION_JSON)
                .content(identityJson));

        String biometricJson = String.format(
                "{\"phone\":\"%s\",\"faceImageBase64\":\"ValidFaceImageBase64String\"," +
                "\"mockLivenessPassed\":true,\"mockFaceMatchPercentage\":95.0}",
                phone);

        mockMvc.perform(post("/api/v1/accounts/verify-biometric")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(biometricJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.cifNumber", notNullValue()))
                .andExpect(jsonPath("$.accountNumber", notNullValue()))
                .andExpect(jsonPath("$.message", containsString("mở tài khoản eKYC thành công")));
    }

    @Test
    void testModule3_VerifyBiometric_LivenessFail_Lockout() throws Exception {
        String phone = "0984567123";
        String registerJson = String.format("{\"phone\":\"%s\",\"email\":\"liveness-fail@abcbank.com.vn\"}", phone);
        mockMvc.perform(post("/api/v1/accounts/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson));

        String verifyOtpJson = String.format("{\"phone\":\"%s\",\"otpCode\":\"123456\"}", phone);
        mockMvc.perform(post("/api/v1/accounts/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyOtpJson));

        String identityJson = String.format(
                "{\"phone\":\"%s\",\"citizenId\":\"036200654321\",\"fullName\":\"NGUYỄN VĂN B\"," +
                "\"dateOfBirth\":\"1995-12-25\",\"gender\":\"MALE\",\"dateOfExpiry\":\"%s\"," +
                "\"identityMethod\":\"NFC\"}",
                phone, LocalDate.now().plusYears(10));
        mockMvc.perform(post("/api/v1/accounts/verify-identity")
                .contentType(MediaType.APPLICATION_JSON)
                .content(identityJson));

        String biometricJson = String.format(
                "{\"phone\":\"%s\",\"faceImageBase64\":\"ValidFaceImageBase64String\"," +
                "\"mockLivenessPassed\":false}",
                phone);

        mockMvc.perform(post("/api/v1/accounts/verify-biometric")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(biometricJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Liveness Check FAIL")));
    }

    @Test
    void testModule4_VerifyBiometric_AMLBlacklist() throws Exception {
        String phone = "0985678123";
        String registerJson = String.format("{\"phone\":\"%s\",\"email\":\"aml@abcbank.com.vn\"}", phone);
        mockMvc.perform(post("/api/v1/accounts/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson));

        String verifyOtpJson = String.format("{\"phone\":\"%s\",\"otpCode\":\"123456\"}", phone);
        mockMvc.perform(post("/api/v1/accounts/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyOtpJson));

        String identityJson = String.format(
                "{\"phone\":\"%s\",\"citizenId\":\"000000000001\",\"fullName\":\"MOCK BLACKLIST USER\"," +
                "\"dateOfBirth\":\"1985-10-10\",\"gender\":\"MALE\",\"dateOfExpiry\":\"%s\"," +
                "\"identityMethod\":\"NFC\"}",
                phone, LocalDate.now().plusYears(10));
        mockMvc.perform(post("/api/v1/accounts/verify-identity")
                .contentType(MediaType.APPLICATION_JSON)
                .content(identityJson));

        String biometricJson = String.format(
                "{\"phone\":\"%s\",\"faceImageBase64\":\"ValidFaceImageBase64\"," +
                "\"mockLivenessPassed\":true,\"mockFaceMatchPercentage\":90.0}",
                phone);

        mockMvc.perform(post("/api/v1/accounts/verify-biometric")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(biometricJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("phòng chống rửa tiền")));
    }
}
