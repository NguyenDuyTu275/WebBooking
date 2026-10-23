package com.Nhom2.booking.config;

import com.Nhom2.booking.controller.AdminReceiptController;
import com.Nhom2.booking.controller.AdminReviewController;
import com.Nhom2.booking.controller.AdminSupportChatController;
import com.Nhom2.booking.controller.SupportChatController;
import com.Nhom2.booking.controller.AuthController;
import com.Nhom2.booking.controller.ChatController;
import com.Nhom2.booking.controller.HotelReviewController;
import com.Nhom2.booking.controller.ImageController;
import com.Nhom2.booking.controller.ReviewController;
import com.Nhom2.booking.controller.RoomController;
import com.Nhom2.booking.controller.UserController;
import com.Nhom2.booking.controller.WishlistController;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.i18n.Messages;
import com.Nhom2.booking.repository.AppConfigRepository;
import com.Nhom2.booking.repository.HotelRepository;
import com.Nhom2.booking.repository.RoomRepository;
import com.Nhom2.booking.security.JwtUtil;
import com.Nhom2.booking.service.ImageStorageService;
import com.Nhom2.booking.service.ReceiptService;
import com.Nhom2.booking.service.ReviewService;
import com.Nhom2.booking.service.SupportChatService;
import com.Nhom2.booking.service.RoomService;
import com.Nhom2.booking.service.UserService;
import com.Nhom2.booking.service.WishlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AdminReceiptController.class,
        AdminReviewController.class,
        AdminSupportChatController.class,
        SupportChatController.class,
        AuthController.class,
        ChatController.class,
        HotelReviewController.class,
        ImageController.class,
        ReviewController.class,
        RoomController.class,
        UserController.class,
        WishlistController.class
})
@Import({SecurityConfig.class, JwtUtil.class, LocaleConfig.class, Messages.class})
class SecurityRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RoomService roomService;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private ImageStorageService imageStorageService;

    @MockitoBean
    private WishlistService wishlistService;

    @MockitoBean
    private SupportChatService supportChatService;

    @MockitoBean
    private AppConfigRepository appConfigRepository;

    @MockitoBean
    private RestTemplate restTemplate;

    @MockitoBean
    private HotelRepository hotelRepository;

    @MockitoBean
    private RoomRepository roomRepository;

    @MockitoBean
    private ReceiptService receiptService;

    @Test
    void debugEndpointThatLeakedPasswordHashesNoLongerExists() throws Exception {
        mockMvc.perform(get("/auth/debug/admin"))
                .andExpect(status().isNotFound());
    }

    @Test
    void chatRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotUploadImages() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});

        mockMvc.perform(multipart("/api/images/hotel/1").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotCreateOrDeleteRooms() throws Exception {
        mockMvc.perform(post("/rooms/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/rooms/1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/rooms/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanUpdateRooms() throws Exception {
        mockMvc.perform(put("/rooms/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanDeleteRooms() throws Exception {
        mockMvc.perform(delete("/rooms/1"))
                .andExpect(status().isOk());
    }

    @Test
    void anyoneCanBrowseRooms() throws Exception {
        mockMvc.perform(get("/rooms/hotel/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void regularUserCanReadOwnProfileButNotTheUserList() throws Exception {
        User alice = new User();
        alice.setUsername("alice");
        when(userService.getByUsername("alice")).thenReturn(alice);

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void regularUserCanChangeOwnEmailViaOtp() throws Exception {
        mockMvc.perform(post("/users/me/email/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/users/me/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"otp\":\"123456\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void anyoneCanReadHotelReviews() throws Exception {
        mockMvc.perform(get("/hotels/1/reviews"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/hotels/1/review-eligibility"))
                .andExpect(status().isOk());
    }

    @Test
    void writingAReviewRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":9,\"hotel\":{\"id\":1}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void onlyAdminsCanListAndReplyToReviews() throws Exception {
        mockMvc.perform(get("/admin/reviews")).andExpect(status().isForbidden());
        mockMvc.perform(put("/admin/reviews/1/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Cảm ơn\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReplyToReviews() throws Exception {
        mockMvc.perform(get("/admin/reviews")).andExpect(status().isOk());
        mockMvc.perform(put("/admin/reviews/1/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Cảm ơn\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void supportChatRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/support-chat/me")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void guestCanChatButCannotReadTheAdminInbox() throws Exception {
        mockMvc.perform(get("/api/support-chat/me")).andExpect(status().isOk());
        mockMvc.perform(post("/api/support-chat/me/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Xin chào\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/support-chat/rooms")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/support-chat/rooms/1/messages")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanReadAndAnswerConversations() throws Exception {
        mockMvc.perform(get("/admin/support-chat/rooms")).andExpect(status().isOk());
        mockMvc.perform(post("/admin/support-chat/rooms/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Dạ còn phòng ạ\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void wishlistRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/wishlist")).andExpect(status().isForbidden());
        mockMvc.perform(put("/wishlist/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void regularUserCanManageOwnWishlistAndAvatar() throws Exception {
        mockMvc.perform(get("/wishlist")).andExpect(status().isOk());
        mockMvc.perform(put("/wishlist/1")).andExpect(status().isOk());
        mockMvc.perform(multipart("/users/me/avatar")
                        .file(new MockMultipartFile("file", "me.png", "image/png", new byte[]{1})))
                .andExpect(status().isOk());
    }

    @Test
    void emailChangeRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/users/me/email/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void receiptScanningRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/receipts/daily"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotScanReceiptsOrReadRevenue() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[]{1});

        mockMvc.perform(multipart("/admin/receipts/scan").file(file))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/receipts/daily"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/receipts").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanScanReceiptsAndReadDailyRevenue() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[]{1});

        mockMvc.perform(multipart("/admin/receipts/scan").file(file))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/receipts/daily").param("date", "2026-10-23"))
                .andExpect(status().isOk());
    }
}
