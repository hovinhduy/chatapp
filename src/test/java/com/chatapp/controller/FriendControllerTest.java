package com.chatapp.controller;

import com.chatapp.dto.request.FriendDto;
import com.chatapp.dto.request.UserDto;
import com.chatapp.model.Friend;
import com.chatapp.security.JwtTokenProvider;
import com.chatapp.service.FriendService;
import com.chatapp.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig
@WebMvcTest(FriendController.class)
public class FriendControllerTest {

    private MockMvc mockMvc;

    @MockBean
    private FriendService friendService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp(WebApplicationContext context) {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Thiết lập dữ liệu test
        userDto1 = new UserDto();
        userDto1.setUserId(1L);
        userDto1.setDisplayName("User One");
        userDto1.setPhone("1234567890");
        userDto1.setCreatedAt(LocalDateTime.now());

        userDto2 = new UserDto();
        userDto2.setUserId(2L);
        userDto2.setDisplayName("User Two");
        userDto2.setPhone("0987654321");
        userDto2.setCreatedAt(LocalDateTime.now());

        friendDto = new FriendDto();
        friendDto.setId(1L);
        friendDto.setUser1(userDto1);
        friendDto.setUser2(userDto2);
        friendDto.setStatus(Friend.FriendStatus.PENDING);
        friendDto.setCreatedAt(LocalDateTime.now());
    }

    private FriendDto friendDto;
    private UserDto userDto1;
    private UserDto userDto2;

    @Test
    @WithMockUser(username = "1234567890")
    void sendFriendRequest_Success() throws Exception {
        when(userService.getUserByPhone("1234567890")).thenReturn(userDto1);
        when(friendService.sendFriendRequest(1L, 2L)).thenReturn(friendDto);

        mockMvc.perform(post("/api/friends/request/2")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.user1.userId").value(1))
                .andExpect(jsonPath("$.user2.userId").value(2));
    }

    @Test
    @WithMockUser(username = "1234567890")
    void acceptFriendRequest_Success() throws Exception {
        friendDto.setStatus(Friend.FriendStatus.ACCEPTED);

        when(userService.getUserByPhone("1234567890")).thenReturn(userDto1);
        when(friendService.acceptFriendRequest(1L, 1L)).thenReturn(friendDto);

        mockMvc.perform(post("/api/friends/accept/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @WithMockUser(username = "1234567890")
    void rejectFriendRequest_Success() throws Exception {
        friendDto.setStatus(Friend.FriendStatus.REJECTED);

        when(userService.getUserByPhone("1234567890")).thenReturn(userDto1);
        when(friendService.rejectFriendRequest(1L, 1L)).thenReturn(friendDto);

        mockMvc.perform(post("/api/friends/reject/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @WithMockUser(username = "1234567890")
    void blockFriend_Success() throws Exception {
        friendDto.setStatus(Friend.FriendStatus.BLOCKED);

        when(userService.getUserByPhone("1234567890")).thenReturn(userDto1);
        when(friendService.blockFriend(1L, 2L)).thenReturn(friendDto);

        mockMvc.perform(post("/api/friends/block/2")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    @WithMockUser(username = "1234567890")
    void getFriends_Success() throws Exception {
        friendDto.setStatus(Friend.FriendStatus.ACCEPTED);

        when(userService.getUserByPhone("1234567890")).thenReturn(userDto1);
        when(friendService.getFriends(1L)).thenReturn(Arrays.asList(friendDto));

        mockMvc.perform(get("/api/friends")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));
    }

    @Test
    @WithMockUser(username = "1234567890")
    void getPendingFriendRequests_Success() throws Exception {
        when(userService.getUserByPhone("1234567890")).thenReturn(userDto1);
        when(friendService.getPendingFriendRequests(1L)).thenReturn(Arrays.asList(friendDto));

        mockMvc.perform(get("/api/friends/pending")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }
}