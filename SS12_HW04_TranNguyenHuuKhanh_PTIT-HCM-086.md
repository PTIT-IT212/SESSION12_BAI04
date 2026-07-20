# Bài tập SS12 - Bài 4: Thiết kế Test Case & Unit Test cho chức năng Đăng ký tài khoản (eKYC)

**Sinh viên:** Trần Đức Anh - N24DTCN003

## Mục lục

1. [Giới thiệu](#gioi-thieu)
2. [Luồng nghiệp vụ eKYC](#luong-nghiep-vu-ekyc)
3. [Quy tắc Validate](#quy-tac-validate)
4. [Danh sách Test Case](#danh-sach-test-case)
5. [Mock Data (CSV)](#mock-data-csv)
6. [Mã nguồn JUnit Test](#mã-nguồn-junit-test)

---

## Giới thiệu

Dự án kiểm thử chức năng **Đăng ký tài khoản eKYC (Electronic Know Your Customer)** cho hệ thống ngân hàng số. Bài tập bao gồm:

- **Phần 1:** Bảng danh sách Test Case toàn diện
- **Phần 2:** Dữ liệu giả lập (Mock Data) 50 bản ghi CSV
- **Phần 3:** Mã nguồn JUnit 5 + Mockito Unit Test

## Luồng nghiệp vụ eKYC

| Bước | Mô tả | Trạng thái |
|------|-------|-----------|
| 1a | Khởi tạo đăng ký (SĐT + Email) | `INITIATED` |
| 1b | Xác thực mã OTP | `OTP_VERIFIED` |
| 2 | Xác thực thông tin CCCD (NFC/OCR) | `IDENTITY_VERIFIED` |
| 3 | Xác thực sinh trắc học khuôn mặt | `APPROVED` / `REJECTED` / `BLOCKED` |

## Quy tắc Validate

| Trường | Ràng buộc |
|--------|-----------|
| `fullName` | 2-50 ký tự, chỉ chứa chữ cái và khoảng trắng |
| `email` | Không trống, đúng định dạng email, duy nhất |
| `phone` | Đúng 10 chữ số, đầu số (03, 05, 07, 08, 09), duy nhất |
| `citizenId` | Đúng 12 chữ số, duy nhất |

---

## Danh sách Test Case

### Module 1a: Khởi tạo đăng ký (Initiate Registration)

| ID | Component | Description | Test Data | Expected Result | Type |
|----|-----------|-------------|-----------|-----------------|------|
| TC01 | initiateRegistration | Tạo mới thành công | phone=0912345678, email=test@domain.com | Account created, status=INITIATED | Positive |
| TC02 | initiateRegistration | Tái sử dụng phiên cũ INITIATED | phone=0912345678 (đã tồn tại, INITIATED) | Cập nhật email, giữ phiên | Positive |
| TC03 | initiateRegistration | SĐT đã được duyệt (APPROVED) | phone=0912345678 (APPROVED) | DuplicateResourceException | Negative |
| TC04 | initiateRegistration | SĐT đang bị khóa (BLOCKED) | phone=0912345678 (BLOCKED, blockedUntil=+12h) | BadRequestException | Negative |
| TC05 | initiateRegistration | Email đã tồn tại (APPROVED) | email=duplicate@domain.com (APPROVED) | DuplicateResourceException | Negative |

### Module 1b: Xác thực OTP (Verify OTP)

| ID | Component | Description | Test Data | Expected Result | Type |
|----|-----------|-------------|-----------|-----------------|------|
| TC06 | verifyOtp | Xác thực OTP thành công | phone=0912345678, otp=123456 (khớp, còn hạn) | status=OTP_VERIFIED, retryCount=0 | Positive |
| TC07 | verifyOtp | SĐT không tồn tại | phone=0000000000 | ResourceNotFoundException | Negative |
| TC08 | verifyOtp | OTP đã hết hạn | otpExpiry = quá khứ | BadRequestException | Negative |
| TC09 | verifyOtp | Nhập sai OTP (lần 1) | otp=wrong (retryCount=0) | BadRequestException, retryCount=1 | Negative |
| TC10 | verifyOtp | Nhập sai OTP 3 lần, khóa tài khoản | otp=wrong (retryCount=2) | SecurityAlertException, status=BLOCKED, blockedUntil=+24h | Negative |

### Module 2: Xác thực CCCD (Verify Identity)

| ID | Component | Description | Test Data | Expected Result | Type |
|----|-----------|-------------|-----------|-----------------|------|
| TC11 | verifyIdentity | Xác thực CCCD qua NFC thành công | citizenId=036200123456, method=NFC | status=IDENTITY_VERIFIED, nfcCaValid=true | Positive |
| TC12 | verifyIdentity | Xác thực CCCD qua OCR thành công | citizenId=036200123456, method=OCR | status=IDENTITY_VERIFIED, nfcCaValid=false | Positive |
| TC13 | verifyIdentity | Chưa xác thực OTP | status=INITIATED | BadRequestException | Negative |
| TC14 | verifyIdentity | CCCD đã được sử dụng | citizenId=036200123456 (duplicate) | DuplicateResourceException | Negative |
| TC15 | verifyIdentity | Giả mạo chữ ký NFC (fake CA) | citizenId=999xxxxxxxxx | SecurityAlertException, status=REJECTED | Negative |
| TC16 | verifyIdentity | CCCD đã hết hạn | dateOfExpiry = yesterday | BadRequestException | Negative |

### Module 3 & 4: Xác thực sinh trắc học & STP (Verify Biometric)

| ID | Component | Description | Test Data | Expected Result | Type |
|----|-----------|-------------|-----------|-----------------|------|
| TC17 | verifyBiometric | Xác thực khuôn mặt + STP thành công | liveness=true, match=92.5% | status=APPROVED, cifNumber!=null, accountNumber!=null | Positive |
| TC18 | verifyBiometric | Liveness check thất bại (giả mạo) | liveness=false | SecurityAlertException, status=BLOCKED, +7 ngày | Negative |
| TC19 | verifyBiometric | Tỷ lệ khớp khuôn mặt thấp | match=75% (<85%) | BadRequestException | Negative |
| TC20 | verifyBiometric | Chưa đủ 18 tuổi | dateOfBirth=17 năm trước | BadRequestException, status=REJECTED | Negative |
| TC21 | verifyBiometric | CCCD nằm trong danh sách đen AML | citizenId=000xxxxxxxxx | BadRequestException, status=REJECTED, amlStatus=FAILED | Negative |

### Input Validation (JSR-380)

| ID | Component | Description | Test Data | Expected Result | Type |
|----|-----------|-------------|-----------|-----------------|------|
| TC22 | DTO Validation | DTO đăng ký hợp lệ | phone=0912345678, email=valid@domain.com | 0 violations | Positive |
| TC23 | DTO Validation | SĐT null | phone=null | Has violations | Negative |
| TC24 | DTO Validation | SĐT rỗng | phone="" | Has violations | Negative |
| TC25 | DTO Validation | SĐT chứa chữ | phone=091234567a | Has violations | Negative |
| TC26 | DTO Validation | SĐT thiếu số (9 số) | phone=091234567 | Has violations | Boundary |
| TC27 | DTO Validation | SĐT thừa số (11 số) | phone=09123456789 | Has violations | Boundary |
| TC28 | DTO Validation | SĐT sai đầu số (02) | phone=0212345678 | Has violations | Negative |
| TC29 | DTO Validation | Email rỗng | email="" | Has violations | Negative |
| TC30 | DTO Validation | Email sai định dạng | email=invalid | Has violations | Negative |
| TC31 | DTO Validation | CCCD thiếu số (11 số) | citizenId=03620012345 | Has violations | Boundary |
| TC32 | DTO Validation | CCCD thừa số (13 số) | citizenId=0362001234567 | Has violations | Boundary |
| TC33 | DTO Validation | Họ tên quá ngắn (1 ký tự) | fullName=A | Has violations | Boundary |
| TC34 | DTO Validation | Họ tên quá dài (>50 ký tự) | fullName=Nguyen Hoang Nam Anh Tuan... (>50) | Has violations | Boundary |
| TC35 | DTO Validation | Họ tên chứa số | fullName=Nguyễn Văn A1 | Has violations | Negative |
| TC36 | DTO Validation | Họ tên chứa ký tự đặc biệt | fullName=Nguyễn Văn A@ | Has violations | Negative |

---

## Mock Data (CSV)

File dữ liệu giả lập: [`mock_customers.csv`](mock_customers.csv)

Gồm 50 dòng dữ liệu:
- **Dòng 1-29 (hợp lệ):** Dữ liệu người dùng Việt Nam ngẫu nhiên với đầy đủ thông tin đúng quy tắc
- **Dòng 30-44 (cố tình sai):** Dữ liệu không hợp lệ để kiểm thử tính năng validate (thiếu email, sai SĐT, sai CCCD, tên quá ngắn/dài, chứa ký tự đặc biệt...)
- **Dòng 45-50 (hợp lệ):** Dữ liệu người dùng bổ sung với đầu số khác

---

## Mã nguồn JUnit Test

Lớp kiểm thử: `AccountServiceUnitTest` sử dụng **JUnit 5** và **Mockito**.

### Cấu trúc Test Class

| Module | Class lồng | Số test | Mô tả |
|--------|-----------|---------|-------|
| 1a | `InitiateRegistrationTests` | 5 | Khởi tạo đăng ký |
| 1b | `VerifyOtpTests` | 5 | Xác thực OTP |
| 2 | `VerifyIdentityTests` | 6 | Xác thực CCCD |
| 3&4 | `VerifyBiometricTests` | 5 | Xác thực sinh trắc học & STP |
| Input | `InputValidationTests` | 7 | Validate DTO (JSR-380) |

### Công nghệ sử dụng

- **JUnit 5** (`@ExtendWith(MockitoExtension.class)`)
- **Mockito** (`@Mock`, `@InjectMocks`)
- **Jakarta Validation (JSR-380)** - kiểm thử annotation constraint
- **AssertJ-style assertions** (`assertEquals`, `assertThrows`, `verify`)

### Source code

```java
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

    // =========================================================================
    // 1. KIỂM THỬ BƯỚC 1: KHỞI TẠO ĐĂNG KÝ (INITIATE REGISTRATION)
    // =========================================================================
    @Nested
    @DisplayName("Module 1: initiateRegistration tests")
    class InitiateRegistrationTests {

        @Test
        @DisplayName("Khởi tạo đăng ký tài khoản mới thành công")
        void initiateRegistration_NewAccount_Success() {
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
        @DisplayName("Tái sử dụng phiên đăng ký cũ chưa hoàn thành eKYC")
        void initiateRegistration_ReuseSession_Success() {
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
        @DisplayName("Ném lỗi DuplicateResourceException khi số điện thoại đã đăng ký và duyệt thành công trước đó")
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
        @DisplayName("Ném lỗi BadRequestException nếu số điện thoại đang nằm trong thời gian bị khóa")
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
        @DisplayName("Ném lỗi DuplicateResourceException khi địa chỉ email đã trùng lắp trong hệ thống")
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

    // =========================================================================
    // 2. KIỂM THỬ BƯỚC 1 (TIẾP TỤC): XÁC THỰC MÃ OTP (VERIFY OTP)
    // =========================================================================
    @Nested
    @DisplayName("Module 1: verifyOtp tests")
    class VerifyOtpTests {

        @Test
        @DisplayName("Xác thực OTP thành công")
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
        @DisplayName("Ném ResourceNotFoundException nếu không thấy số điện thoại đăng ký")
        void verifyOtp_NotFound_ThrowsException() {
            OtpVerificationRequest request = new OtpVerificationRequest("0912345678", "123456");
            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> accountService.verifyOtp(request));
        }

        @Test
        @DisplayName("Ném BadRequestException khi mã OTP đã hết hiệu lực")
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
        @DisplayName("Nhập sai mã OTP tăng bộ đếm số lần sai và báo lỗi BadRequestException")
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
        @DisplayName("Nhập sai OTP liên tiếp quá 3 lần khóa tài khoản và ném SecurityAlertException")
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

    // =========================================================================
    // 3. KIỂM THỬ BƯỚC 2: XÁC THỰC THÔNG TIN GIẤY TỜ CCCD (VERIFY IDENTITY)
    // =========================================================================
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
        @DisplayName("Xác thực thông tin CCCD qua NFC thành công")
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
        @DisplayName("Xác thực thông tin CCCD bằng OCR thành công khi thiết bị không hỗ trợ NFC")
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
        @DisplayName("Ném BadRequestException nếu tài khoản chưa hoàn tất bước xác thực OTP")
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
        @DisplayName("Ném DuplicateResourceException nếu số định danh CCCD đã được sử dụng và duyệt thành công ở tài khoản khác")
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
        @DisplayName("Ném SecurityAlertException khi giả mạo chữ ký số NFC (Số CCCD bắt đầu bằng 999)")
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
        @DisplayName("Ném BadRequestException khi căn cước công dân đã hết hạn sử dụng")
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

    // =========================================================================
    // 4. KIỂM THỬ BƯỚC 3 & 4: SINH TRẮC HỌC & DUYỆT TỰ ĐỘNG (VERIFY BIOMETRIC & STP)
    // =========================================================================
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
        @DisplayName("Xác thực khuôn mặt thành công và tự động phê duyệt STP cấp số tài khoản")
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
        @DisplayName("Ném SecurityAlertException và khóa đăng ký 7 ngày khi phát hiện giả mạo khuôn mặt (Liveness Check FAIL)")
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
        @DisplayName("Ném BadRequestException khi tỷ lệ so khớp khuôn mặt nhỏ hơn ngưỡng 85%")
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
        @DisplayName("Từ chối duyệt và ném BadRequestException nếu tuổi thực tế của khách hàng chưa đủ 18")
        void verifyBiometric_Underage_ThrowsException() {
            BiometricVerificationRequest request = createValidRequest();
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .fullName("NGUYỄN VĂN C")
                    .citizenId("036200123456")
                    .dateOfBirth(LocalDate.now().minusYears(17))
                    .status(KycStatus.IDENTITY_VERIFIED)
                    .build();

            when(accountRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(account));

            BadRequestException ex = assertThrows(BadRequestException.class, () -> accountService.verifyBiometric(request));
            assertTrue(ex.getMessage().contains("chưa đủ 18 tuổi"));
            assertEquals(KycStatus.REJECTED, account.getStatus());
            verify(accountRepository, times(1)).save(account);
        }

        @Test
        @DisplayName("Từ chối duyệt và ném BadRequestException nếu CCCD nằm trong danh sách đen rửa tiền AML")
        void verifyBiometric_AmlBlacklist_ThrowsException() {
            BiometricVerificationRequest request = createValidRequest();
            Account account = Account.builder()
                    .id(1L)
                    .phone("0912345678")
                    .fullName("NGUYỄN VĂN A")
                    .citizenId("000200123456")
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

    // =========================================================================
    // 5. KIỂM THỬ CÁC RÀNG BUỘC DỮ LIỆU ĐẦU VÀO JSR-380 (DTO VALIDATIONS)
    // =========================================================================
    @Nested
    @DisplayName("Input Validation (JSR-380) Constraint tests")
    class InputValidationTests {

        @Test
        @DisplayName("Validation thành công với DTO đăng ký hợp lệ")
        void validateRegisterRequest_Success() {
            AccountRegistrationRequest request = new AccountRegistrationRequest("0912345678", "valid@domain.com");
            Set<ConstraintViolation<AccountRegistrationRequest>> violations = validator.validate(request);
            assertTrue(violations.isEmpty(), "Dữ liệu hợp lệ không được có lỗi validation");
        }

        @Test
        @DisplayName("Validation thất bại khi số điện thoại không hợp lệ")
        void validateRegisterRequest_InvalidPhone() {
            AccountRegistrationRequest req1 = new AccountRegistrationRequest(null, "valid@domain.com");
            assertFalse(validator.validate(req1).isEmpty());

            AccountRegistrationRequest req2 = new AccountRegistrationRequest("", "valid@domain.com");
            assertFalse(validator.validate(req2).isEmpty());

            AccountRegistrationRequest req3 = new AccountRegistrationRequest("091234567a", "valid@domain.com");
            assertFalse(validator.validate(req3).isEmpty());

            AccountRegistrationRequest req4 = new AccountRegistrationRequest("091234567", "valid@domain.com");
            assertFalse(validator.validate(req4).isEmpty());

            AccountRegistrationRequest req5 = new AccountRegistrationRequest("09123456789", "valid@domain.com");
            assertFalse(validator.validate(req5).isEmpty());

            AccountRegistrationRequest req6 = new AccountRegistrationRequest("0212345678", "valid@domain.com");
            assertFalse(validator.validate(req6).isEmpty());
        }

        @Test
        @DisplayName("Validation thất bại khi email không hợp lệ")
        void validateRegisterRequest_InvalidEmail() {
            AccountRegistrationRequest req1 = new AccountRegistrationRequest("0912345678", "");
            assertFalse(validator.validate(req1).isEmpty());

            AccountRegistrationRequest req2 = new AccountRegistrationRequest("0912345678", "invalid_email_format");
            assertFalse(validator.validate(req2).isEmpty());
        }

        @Test
        @DisplayName("Validation thành công với DTO xác thực định danh hợp lệ")
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
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Validation thất bại khi citizenId (CCCD) sai định dạng số chữ số")
        void validateIdentityRequest_InvalidCitizenId() {
            IdentityVerificationRequest request = IdentityVerificationRequest.builder()
                    .phone("0912345678")
                    .citizenId("03620012345")
                    .fullName("NGUYỄN VĂN A")
                    .dateOfBirth(LocalDate.of(2000, 1, 1))
                    .gender(Gender.MALE)
                    .dateOfExpiry(LocalDate.now().plusYears(10))
                    .identityMethod(IdentityMethod.NFC)
                    .build();

            assertFalse(validator.validate(request).isEmpty());

            request.setCitizenId("0362001234567");
            assertFalse(validator.validate(request).isEmpty());
        }

        @Test
        @DisplayName("Validation thất bại khi Họ và tên vi phạm các quy tắc nghiệp vụ")
        void validateIdentityRequest_InvalidFullName() {
            IdentityVerificationRequest request = IdentityVerificationRequest.builder()
                    .phone("0912345678")
                    .citizenId("036200123456")
                    .fullName("A")
                    .dateOfBirth(LocalDate.of(2000, 1, 1))
                    .gender(Gender.MALE)
                    .dateOfExpiry(LocalDate.now().plusYears(10))
                    .identityMethod(IdentityMethod.NFC)
                    .build();

            assertFalse(validator.validate(request).isEmpty());

            request.setFullName("Nguyen Hoang Nam Anh Tuan Khanh Duy Lam Phong Son Nguyen");
            assertFalse(validator.validate(request).isEmpty());

            request.setFullName("Nguyễn Văn A1");
            assertFalse(validator.validate(request).isEmpty());

            request.setFullName("Nguyễn Văn A@");
            assertFalse(validator.validate(request).isEmpty());
        }
    }
}
```