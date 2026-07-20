package com.re.ss12sroucecode.service;

import com.re.ss12sroucecode.dto.*;
import com.re.ss12sroucecode.entity.Account;
import com.re.ss12sroucecode.enums.Gender;
import com.re.ss12sroucecode.enums.IdentityMethod;
import com.re.ss12sroucecode.enums.KycStatus;
import com.re.ss12sroucecode.exception.*;
import com.re.ss12sroucecode.repository.AccountRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceUnitTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountServiceImpl accountService;

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==========================================
    // MODULE 1: INITIATE REGISTRATION TESTS
    // ==========================================
    @Nested
    @DisplayName("Module 1: initiateRegistration tests")
    class InitiateRegistrationTests {

        @Test
        @DisplayName("Create new account registration successfully")
        void initiateRegistration_NewAccount_Success() {
            // Data Flow: Request -> FindByPhone (empty) -> FindByEmail (empty) -> Save -> Return Response
            AccountRegistrationRequest request = new AccountRegistrationRequest("0912345678", "test@domain.com");

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.empty());
            when(accountRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
                Account savedAcc = invocation.getArgument(0);
                savedAcc.setId(1L);
                return savedAcc;
            });

            AccountRegistrationResponse response = accountService.initiateRegistration(request);

            assertNotNull(response);
            assertEquals(1L, response.getId());
            assertEquals(request.getPhone(), response.getPhone());
            assertEquals(request.getEmail(), response.getEmail());
            assertEquals(KycStatus.INITIATED, response.getStatus());
            verify(accountRepository, times(1)).save(any(Account.class));
        }

        @Test
        @DisplayName("Reuse existing uncompleted registration session")
        void initiateRegistration_ReuseSession_Success() {
            // Data Flow: Request -> FindByPhone (exists but not APPROVED) -> FindByEmail (empty) -> Update existing -> Save
            AccountRegistrationRequest request = new AccountRegistrationRequest("0912345678", "newemail@domain.com");
            Account existingAccount = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .email("old@domain.com")
                    .status(KycStatus.INITIATED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(existingAccount));
            when(accountRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

            AccountRegistrationResponse response = accountService.initiateRegistration(request);

            assertNotNull(response);
            assertEquals(1L, response.getId());
            assertEquals("newemail@domain.com", existingAccount.getEmail());
            assertEquals(KycStatus.INITIATED, response.getStatus());
            verify(accountRepository, times(1)).save(existingAccount);
        }

        @Test
        @DisplayName("Throw DuplicateResourceException if phone already registered and approved")
        void initiateRegistration_DuplicatePhone_ThrowsException() {
            AccountRegistrationRequest request = new AccountRegistrationRequest("0912345678", "test@domain.com");
            Account approvedAccount = Account.builder()
                    .id(2L)
                    .phone("0912345678")
                    .status(KycStatus.APPROVED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(approvedAccount));

            assertThrows(DuplicateResourceException.class, () -> accountService.initiateRegistration(request));
            verify(accountRepository, never()).save(any(Account.class));
        }

        @Test
        @DisplayName("Throw BadRequestException if phone is currently locked")
        void initiateRegistration_LockedPhone_ThrowsException() {
            AccountRegistrationRequest request = new AccountRegistrationRequest("0912345678", "test@domain.com");
            Account blockedAccount = Account.builder()
                    .id(2L)
                    .phone("0912345678")
                    .status(KycStatus.BLOCKED)
                    .blockedUntil(LocalDateTime.now().plusHours(12))
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(blockedAccount));

            assertThrows(BadRequestException.class, () -> accountService.initiateRegistration(request));
            verify(accountRepository, never()).save(any(Account.class));
        }

        @Test
        @DisplayName("Throw DuplicateResourceException if email already registered and approved")
        void initiateRegistration_DuplicateEmail_ThrowsException() {
            AccountRegistrationRequest request = new AccountRegistrationRequest("0912345678", "duplicate@domain.com");

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.empty());
            when(accountRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(
                    Account.builder().id(3L).email("duplicate@domain.com").status(KycStatus.APPROVED).build()
            ));

            assertThrows(DuplicateResourceException.class, () -> accountService.initiateRegistration(request));
            verify(accountRepository, never()).save(any(Account.class));
        }
    }

    // ==========================================
    // MODULE 1 (CONTINUED): VERIFY OTP TESTS
    // ==========================================
    @Nested
    @DisplayName("Module 1: verifyOtp tests")
    class VerifyOtpTests {

        @Test
        @DisplayName("Verify OTP successfully")
        void verifyOtp_Success() {
            OtpVerificationRequest request = new OtpVerificationRequest("0912345678", "123456");
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .otpCode("123456")
                    .otpExpiry(LocalDateTime.now().plusMinutes(3))
                    .otpRetryCount(1)
                    .status(KycStatus.INITIATED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(account));

            AccountRegistrationResponse response = accountService.verifyOtp(request);

            assertNotNull(response);
            assertEquals(KycStatus.OTP_VERIFIED, account.getStatus());
            assertEquals(0, account.getOtpRetryCount());
            verify(accountRepository, times(1)).save(account);
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when phone registration session not found")
        void verifyOtp_NotFound_ThrowsException() {
            OtpVerificationRequest request = new OtpVerificationRequest("0912345678", "123456");
            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> accountService.verifyOtp(request));
        }

        @Test
        @DisplayName("Throw BadRequestException when OTP has expired")
        void verifyOtp_Expired_ThrowsException() {
            OtpVerificationRequest request = new OtpVerificationRequest("0912345678", "123456");
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .otpCode("123456")
                    .otpExpiry(LocalDateTime.now().minusSeconds(1))
                    .status(KycStatus.INITIATED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(account));

            assertThrows(BadRequestException.class, () -> accountService.verifyOtp(request));
        }

        @Test
        @DisplayName("OTP incorrect increments retry count and throws BadRequestException")
        void verifyOtp_IncorrectOtp_IncrementsRetry() {
            OtpVerificationRequest request = new OtpVerificationRequest("0912345678", "wrong_otp");
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .otpCode("123456")
                    .otpExpiry(LocalDateTime.now().plusMinutes(3))
                    .otpRetryCount(0)
                    .status(KycStatus.INITIATED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(account));

            BadRequestException ex = assertThrows(BadRequestException.class, () -> accountService.verifyOtp(request));
            assertTrue(ex.getMessage().contains("Số lần thử còn lại: 2/3"));
            assertEquals(1, account.getOtpRetryCount());
            verify(accountRepository, times(1)).save(account);
        }

        @Test
        @DisplayName("OTP incorrect 3 times blocks the account and throws SecurityAlertException")
        void verifyOtp_IncorrectOtp3Times_BlocksAccount() {
            OtpVerificationRequest request = new OtpVerificationRequest("0912345678", "wrong_otp");
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .otpCode("123456")
                    .otpExpiry(LocalDateTime.now().plusMinutes(3))
                    .otpRetryCount(2)
                    .status(KycStatus.INITIATED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(account));

            SecurityAlertException ex = assertThrows(SecurityAlertException.class, () -> accountService.verifyOtp(request));
            assertTrue(ex.getMessage().contains("bị khóa trong 24 giờ"));
            assertEquals(KycStatus.BLOCKED, account.getStatus());
            assertNotNull(account.getBlockedUntil());
            verify(accountRepository, times(1)).save(account);
        }
    }

    // ==========================================
    // MODULE 2: VERIFY IDENTITY TESTS
    // ==========================================
    @Nested
    @DisplayName("Module 2: verifyIdentity tests")
    class VerifyIdentityTests {

        private IdentityVerificationRequest createValidRequest() {
            return IdentityVerificationRequest.builder()
                    .phone("0912345678")
                    .citizenId("036200123456")
                    .fullName("NGUYỄN VĂN A")
                    .dateOfBirth(LocalDate.of(2000, 1, 1))
                    .gender(Gender.MALE)
                    .dateOfExpiry(LocalDate.now().plusYears(10))
                    .identityMethod(IdentityMethod.NFC)
                    .build();
        }

        @Test
        @DisplayName("Verify Identity successfully with NFC")
        void verifyIdentity_Nfc_Success() {
            IdentityVerificationRequest request = createValidRequest();
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .status(KycStatus.OTP_VERIFIED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(account));
            when(accountRepository.existsByCitizenIdExcludeId(request.getCitizenId(), account.getId())).thenReturn(false);

            AccountRegistrationResponse response = accountService.verifyIdentity(request);

            assertNotNull(response);
            assertEquals(KycStatus.IDENTITY_VERIFIED, account.getStatus());
            assertEquals("036200123456", account.getCitizenId());
            assertEquals("NGUYỄN VĂN A", account.getFullName());
            assertTrue(account.getNfcCaValid());
            verify(accountRepository, times(1)).save(account);
        }

        @Test
        @DisplayName("Verify Identity successfully with OCR (NFC is invalid/unsupported)")
        void verifyIdentity_Ocr_Success() {
            IdentityVerificationRequest request = createValidRequest();
            request.setIdentityMethod(IdentityMethod.OCR);
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .status(KycStatus.OTP_VERIFIED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(account));
            when(accountRepository.existsByCitizenIdExcludeId(request.getCitizenId(), account.getId())).thenReturn(false);

            AccountRegistrationResponse response = accountService.verifyIdentity(request);

            assertNotNull(response);
            assertEquals(KycStatus.IDENTITY_VERIFIED, account.getStatus());
            assertFalse(account.getNfcCaValid());
            verify(accountRepository, times(1)).save(account);
        }

        @Test
        @DisplayName("Throw BadRequestException if step 1 OTP verification not done")
        void verifyIdentity_NotVerifiedOtp_ThrowsException() {
            IdentityVerificationRequest request = createValidRequest();
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .status(KycStatus.INITIATED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(account));

            assertThrows(BadRequestException.class, () -> accountService.verifyIdentity(request));
            verify(accountRepository, never()).save(any(Account.class));
        }

        @Test
        @DisplayName("Throw DuplicateResourceException if citizenId is already approved on another account")
        void verifyIdentity_DuplicateCitizenId_ThrowsException() {
            IdentityVerificationRequest request = createValidRequest();
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .status(KycStatus.OTP_VERIFIED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(account));
            when(accountRepository.existsByCitizenIdExcludeId(request.getCitizenId(), account.getId())).thenReturn(true);
            when(accountRepository.findByCitizenId(request.getCitizenId())).thenReturn(Optional.of(
                    Account.builder().id(2L).citizenId(request.getCitizenId()).status(KycStatus.APPROVED).build()
            ));

            assertThrows(DuplicateResourceException.class, () -> accountService.verifyIdentity(request));
        }

        @Test
        @DisplayName("Throw SecurityAlertException if citizenId starts with 999 (Simulated fake NFC signature)")
        void verifyIdentity_FakeNfcSignature_ThrowsException() {
            IdentityVerificationRequest request = createValidRequest();
            request.setCitizenId("999123456789");
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .status(KycStatus.OTP_VERIFIED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(account));
            when(accountRepository.existsByCitizenIdExcludeId(request.getCitizenId(), account.getId())).thenReturn(false);

            SecurityAlertException ex = assertThrows(SecurityAlertException.class, () -> accountService.verifyIdentity(request));
            assertTrue(ex.getMessage().contains("CA INVALID"));
            assertEquals(KycStatus.REJECTED, account.getStatus());
            verify(accountRepository, times(1)).save(account);
        }

        @Test
        @DisplayName("Throw BadRequestException if citizen ID card is expired")
        void verifyIdentity_ExpiredCard_ThrowsException() {
            IdentityVerificationRequest request = createValidRequest();
            request.setDateOfExpiry(LocalDate.now().minusDays(1));
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .status(KycStatus.OTP_VERIFIED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(account));

            assertThrows(BadRequestException.class, () -> accountService.verifyIdentity(request));
        }
    }

    // ==========================================
    // MODULE 3 & 4: BIOMETRIC VERIFICATION TESTS
    // ==========================================
    @Nested
    @DisplayName("Module 3 & 4: verifyBiometric tests")
    class VerifyBiometricTests {

        private BiometricVerificationRequest createValidRequest() {
            return BiometricVerificationRequest.builder()
                    .phone("0912345678")
                    .faceImageBase64("ValidBase64Data")
                    .mockLivenessPassed(true)
                    .mockFaceMatchPercentage(92.5)
                    .build();
        }

        @Test
        @DisplayName("Verify Biometric & Auto-approve account successfully (STP Rate 100%)")
        void verifyBiometric_Success() {
            BiometricVerificationRequest request = createValidRequest();
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .fullName("NGUYỄN VĂN A")
                    .citizenId("036200123456")
                    .dateOfBirth(LocalDate.of(2000, 1, 1))
                    .identityMethod(IdentityMethod.NFC)
                    .status(KycStatus.IDENTITY_VERIFIED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(account));

            AccountRegistrationResponse response = accountService.verifyBiometric(request);

            assertNotNull(response);
            assertEquals(KycStatus.APPROVED, account.getStatus());
            assertEquals("CLEAR", account.getAmlStatus());
            assertNotNull(account.getCifNumber());
            assertNotNull(account.getAccountNumber());
            assertTrue(account.getLivenessPassed());
            assertEquals(92.5, account.getFaceMatchPercentage());
            verify(accountRepository, times(1)).save(account);
        }

        @Test
        @DisplayName("Throw SecurityAlertException and block account for 7 days when Liveness fails")
        void verifyBiometric_LivenessFail_BlocksAccount() {
            BiometricVerificationRequest request = createValidRequest();
            request.setMockLivenessPassed(false);
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .status(KycStatus.IDENTITY_VERIFIED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(account));

            SecurityAlertException ex = assertThrows(SecurityAlertException.class, () -> accountService.verifyBiometric(request));
            assertTrue(ex.getMessage().contains("Liveness Check FAIL"));
            assertEquals(KycStatus.BLOCKED, account.getStatus());
            assertNotNull(account.getBlockedUntil());
            verify(accountRepository, times(1)).save(account);
        }

        @Test
        @DisplayName("Throw BadRequestException if face match percentage < 85%")
        void verifyBiometric_LowFaceMatch_ThrowsException() {
            BiometricVerificationRequest request = createValidRequest();
            request.setMockFaceMatchPercentage(75.0);
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .status(KycStatus.IDENTITY_VERIFIED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(account));

            BadRequestException ex = assertThrows(BadRequestException.class, () -> accountService.verifyBiometric(request));
            assertTrue(ex.getMessage().contains("Khuôn mặt chụp thực tế không khớp"));
            verify(accountRepository, times(1)).save(account);
        }

        @Test
        @DisplayName("Throw BadRequestException and reject if user is under 18 years old")
        void verifyBiometric_Underage_ThrowsException() {
            BiometricVerificationRequest request = createValidRequest();
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .fullName("NGUYỄN VĂN C")
                    .citizenId("036200123456")
                    .dateOfBirth(LocalDate.now().minusYears(17)) // 17 years old
                    .status(KycStatus.IDENTITY_VERIFIED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(account));

            BadRequestException ex = assertThrows(BadRequestException.class, () -> accountService.verifyBiometric(request));
            assertTrue(ex.getMessage().contains("chưa đủ 18 tuổi"));
            assertEquals(KycStatus.REJECTED, account.getStatus());
            verify(accountRepository, times(1)).save(account);
        }

        @Test
        @DisplayName("Throw BadRequestException and reject if citizenId matches AML Blacklist")
        void verifyBiometric_AmlBlacklist_ThrowsException() {
            BiometricVerificationRequest request = createValidRequest();
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .fullName("NGUYỄN VĂN A")
                    .citizenId("000200123456") // Starts with 000 simulates AML Blacklist
                    .dateOfBirth(LocalDate.of(2000, 1, 1))
                    .status(KycStatus.IDENTITY_VERIFIED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(account));

            BadRequestException ex = assertThrows(BadRequestException.class, () -> accountService.verifyBiometric(request));
            assertTrue(ex.getMessage().contains("phòng chống rửa tiền"));
            assertEquals(KycStatus.REJECTED, account.getStatus());
            assertEquals("FAILED", account.getAmlStatus());
            verify(accountRepository, times(1)).save(account);
        }
    }

    // ==========================================
    // JSR-380 INPUT VALIDATION CONSTRAINTS TESTS
    // ==========================================
    @Nested
    @DisplayName("Input Validation (JSR-380) Constraint tests")
    class InputValidationTests {

        @Test
        @DisplayName("Validate AccountRegistrationRequest: Valid phone and email")
        void validateRegisterRequest_Success() {
            AccountRegistrationRequest request = new AccountRegistrationRequest("0912345678", "valid@domain.com");
            Set<ConstraintViolation<AccountRegistrationRequest>> violations = validator.validate(request);
            assertTrue(violations.isEmpty(), "Should not have validation errors");
        }

        @Test
        @DisplayName("Validate AccountRegistrationRequest: Invalid phone cases")
        void validateRegisterRequest_InvalidPhone() {
            // Null phone
            AccountRegistrationRequest req1 = new AccountRegistrationRequest(null, "valid@domain.com");
            assertFalse(validator.validate(req1).isEmpty(), "Null phone should trigger violation");

            // Empty phone
            AccountRegistrationRequest req2 = new AccountRegistrationRequest("", "valid@domain.com");
            assertFalse(validator.validate(req2).isEmpty(), "Empty phone should trigger violation");

            // Phone contains letters
            AccountRegistrationRequest req3 = new AccountRegistrationRequest("091234567a", "valid@domain.com");
            assertFalse(validator.validate(req3).isEmpty(), "Phone containing letters should trigger violation");

            // Phone length too short (9 digits)
            AccountRegistrationRequest req4 = new AccountRegistrationRequest("091234567", "valid@domain.com");
            assertFalse(validator.validate(req4).isEmpty(), "Phone length 9 should trigger violation");

            // Phone length too long (11 digits)
            AccountRegistrationRequest req5 = new AccountRegistrationRequest("09123456789", "valid@domain.com");
            assertFalse(validator.validate(req5).isEmpty(), "Phone length 11 should trigger violation");

            // Phone starting with invalid VietNam prefix (e.g. 02)
            AccountRegistrationRequest req6 = new AccountRegistrationRequest("0212345678", "valid@domain.com");
            assertFalse(validator.validate(req6).isEmpty(), "Invalid VN carrier prefix should trigger violation");
        }

        @Test
        @DisplayName("Validate AccountRegistrationRequest: Invalid email cases")
        void validateRegisterRequest_InvalidEmail() {
            // Null email
            AccountRegistrationRequest req1 = new AccountRegistrationRequest("0912345678", null);
            assertFalse(validator.validate(req1).isEmpty(), "Null email should trigger violation");

            // Empty email
            AccountRegistrationRequest req2 = new AccountRegistrationRequest("0912345678", "");
            assertFalse(validator.validate(req2).isEmpty(), "Empty email should trigger violation");

            // Incorrect email format (missing @)
            AccountRegistrationRequest req3 = new AccountRegistrationRequest("0912345678", "invalid_email.com");
            assertFalse(validator.validate(req3).isEmpty(), "Incorrect email format should trigger violation");
        }

        @Test
        @DisplayName("Validate IdentityVerificationRequest: Valid fields")
        void validateIdentityRequest_Success() {
            IdentityVerificationRequest request = IdentityVerificationRequest.builder()
                    .phone("0912345678")
                    .citizenId("036200123456")
                    .fullName("NGUYỄN VĂN A")
                    .dateOfBirth(LocalDate.of(2000, 1, 1))
                    .gender(Gender.MALE)
                    .dateOfExpiry(LocalDate.now().plusYears(10))
                    .identityMethod(IdentityMethod.NFC)
                    .build();

            Set<ConstraintViolation<IdentityVerificationRequest>> violations = validator.validate(request);
            assertTrue(violations.isEmpty(), "Should not have validation errors");
        }

        @Test
        @DisplayName("Validate IdentityVerificationRequest: Invalid citizenId")
        void validateIdentityRequest_InvalidCitizenId() {
            IdentityVerificationRequest request = IdentityVerificationRequest.builder()
                    .phone("0912345678")
                    .citizenId("03620012345") // 11 digits (too short)
                    .fullName("NGUYỄN VĂN A")
                    .dateOfBirth(LocalDate.of(2000, 1, 1))
                    .gender(Gender.MALE)
                    .dateOfExpiry(LocalDate.now().plusYears(10))
                    .identityMethod(IdentityMethod.NFC)
                    .build();

            assertFalse(validator.validate(request).isEmpty(), "Too short citizenId should trigger violation");

            request.setCitizenId("0362001234567"); // 13 digits (too long)
            assertFalse(validator.validate(request).isEmpty(), "Too long citizenId should trigger violation");

            request.setCitizenId("03620012345a"); // Contains character
            assertFalse(validator.validate(request).isEmpty(), "Non-digit citizenId should trigger violation");
        }

        @Test
        @DisplayName("Validate IdentityVerificationRequest: Invalid fullName")
        void validateIdentityRequest_InvalidFullName() {
            IdentityVerificationRequest request = IdentityVerificationRequest.builder()
                    .phone("0912345678")
                    .citizenId("036200123456")
                    .fullName("A") // Too short (min 2)
                    .dateOfBirth(LocalDate.of(2000, 1, 1))
                    .gender(Gender.MALE)
                    .dateOfExpiry(LocalDate.now().plusYears(10))
                    .identityMethod(IdentityMethod.NFC)
                    .build();

            assertFalse(validator.validate(request).isEmpty(), "Too short fullName should trigger violation");

            // Too long (>50 characters)
            request.setFullName("Nguyen Hoang Nam Anh Tuan Khanh Duy Lam Phong Son Nguyen");
            assertFalse(validator.validate(request).isEmpty(), "Too long fullName should trigger violation");

            // Contains digits
            request.setFullName("Nguyễn Văn A1");
            assertFalse(validator.validate(request).isEmpty(), "fullName containing digits should trigger violation");

            // Contains special characters
            request.setFullName("Nguyễn Văn A@");
            assertFalse(validator.validate(request).isEmpty(), "fullName containing special character should trigger violation");
        }
    }
}
