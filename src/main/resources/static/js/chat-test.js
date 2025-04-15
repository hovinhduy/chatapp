// Biến toàn cục
let stompClient = null;
let currentUser = null;
let currentChat = null;
let token = null;
let conversations = {};

// Các phần tử DOM
const loginBtn = document.getElementById("login-btn");
const logoutBtn = document.getElementById("logout-btn");
const sendBtn = document.getElementById("send-btn");
const messageInput = document.getElementById("message");
const messagesContainer = document.getElementById("messages");
const loginSection = document.querySelector(".login-section");
const chatSection = document.querySelector(".chat-section");
const loggedUserSpan = document.getElementById("logged-user");
const loginStatus = document.getElementById("login-status");
const friendsList = document.getElementById("friends-list");
const conversationsList = document.getElementById("conversations-list");
const chatWithSpan = document.getElementById("chat-with");

// Sự kiện khi trang được tải
document.addEventListener("DOMContentLoaded", () => {
  // Kiểm tra xem đã có token trong localStorage chưa
  token = localStorage.getItem("chatToken");
  if (token) {
    fetchCurrentUser();
  }
});

// Sự kiện đăng nhập
loginBtn.addEventListener("click", login);

// Sự kiện đăng xuất
logoutBtn.addEventListener("click", logout);

// Sự kiện gửi tin nhắn
sendBtn.addEventListener("click", sendMessage);
messageInput.addEventListener("keypress", (e) => {
  if (e.key === "Enter") {
    sendMessage();
  }
});

// Hàm đăng nhập
async function login() {
  const phone = document.getElementById("phone").value;
  const password = document.getElementById("password").value;

  if (!phone || !password) {
    loginStatus.textContent = "Vui lòng nhập số điện thoại và mật khẩu";
    return;
  }

  try {
    const response = await fetch("/api/auth/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ phone, password }),
    });

    const data = await response.json();

    if (response.ok) {
      token = data.payload.accessToken;
      localStorage.setItem("chatToken", token);
      fetchCurrentUser();
    } else {
      loginStatus.textContent = data.message || "Đăng nhập thất bại";
    }
  } catch (error) {
    console.error("Lỗi đăng nhập:", error);
    loginStatus.textContent = "Đã xảy ra lỗi khi đăng nhập";
  }
}

// Lấy thông tin người dùng hiện tại
async function fetchCurrentUser() {
  try {
    const response = await fetch("/api/users/me", {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    if (response.ok) {
      currentUser = await response.json();
      loggedUserSpan.textContent = currentUser.displayName || currentUser.phone;

      loginSection.style.display = "none";
      chatSection.style.display = "block";

      // Kết nối WebSocket sau khi đăng nhập thành công
      connectWebSocket();

      // Tải danh sách bạn bè và cuộc trò chuyện
      fetchFriends();
      fetchConversations();
    } else {
      localStorage.removeItem("chatToken");
      token = null;
    }
  } catch (error) {
    console.error("Lỗi khi lấy thông tin người dùng:", error);
    localStorage.removeItem("chatToken");
    token = null;
  }
}

// Hiển thị giao diện chat
function showChatInterface() {
  loginSection.style.display = "none";
  chatSection.style.display = "block";
  loggedUserSpan.textContent = currentUser.displayName || currentUser.phone;
}

// Kết nối WebSocket
function connectWebSocket() {
  const socket = new SockJS("/ws");
  stompClient = Stomp.over(socket);

  stompClient.connect(
    {},
    function (frame) {
      console.log("Kết nối WebSocket thành công: " + frame);

      // Đăng ký nhận tin nhắn từ các cuộc trò chuyện
      if (currentUser) {
        // Đăng ký nhận tin nhắn từ tất cả các cuộc trò chuyện
        Object.keys(conversations).forEach((conversationId) => {
          subscribeToConversation(conversationId);
        });
      }
    },
    function (error) {
      console.error("Lỗi kết nối WebSocket:", error);
      setTimeout(connectWebSocket, 5000); // Thử kết nối lại sau 5 giây
    }
  );
}

function subscribeToConversation(conversationId) {
  if (stompClient && stompClient.connected) {
    stompClient.subscribe(
      `/topic/conversation/${conversationId}`,
      function (message) {
        const receivedMessage = JSON.parse(message.body);
        handleIncomingMessage(receivedMessage);
      }
    );
    console.log(
      `Đã đăng ký nhận tin nhắn từ cuộc trò chuyện ${conversationId}`
    );
  }
}

// Lấy danh sách bạn bè
async function fetchFriends() {
  try {
    const response = await fetch("/api/friends", {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    if (response.ok) {
      const friends = await response.json();
      displayFriends(friends);
    }
  } catch (error) {
    console.error("Lỗi lấy danh sách bạn bè:", error);
  }
}

// Hiển thị danh sách bạn bè
function displayFriends(friends) {
  console.log("Hiển thị danh sách bạn bè:", friends);
  friendsList.innerHTML = "";

  if (friends.length === 0) {
    friendsList.innerHTML = "<p>Chưa có bạn bè</p>";
    return;
  }

  friends.forEach((friend) => {
    const otherUser =
      friend.user1.userId === currentUser.userId ? friend.user2 : friend.user1;

    console.log("Hiển thị bạn bè:", otherUser);

    const friendItem = document.createElement("div");
    friendItem.className = "friend-item";
    friendItem.textContent = otherUser.displayName || otherUser.phone;
    friendItem.dataset.userId = otherUser.userId;

    friendItem.addEventListener("click", () => {
      console.log("Click vào bạn bè:", otherUser);
      startChat(otherUser);
    });

    friendsList.appendChild(friendItem);
  });
}

// Thêm hàm mới để bắt đầu chat với người dùng
async function startChatWithUser(user) {
  try {
    // Tìm hoặc tạo cuộc trò chuyện
    const response = await fetch(`/api/conversations/user/${user.userId}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
    });

    if (response.ok) {
      const conversation = await response.json();
      // Chọn cuộc trò chuyện này
      selectConversation(conversation);
      // Cập nhật lại danh sách cuộc trò chuyện
      fetchConversations();
    }
  } catch (error) {
    console.error("Lỗi khi bắt đầu cuộc trò chuyện:", error);
  }
}

// Lấy danh sách cuộc trò chuyện
async function fetchConversations() {
  try {
    const response = await fetch("/api/conversations", {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    if (response.ok) {
      const conversationsData = await response.json();
      displayConversations(conversationsData);
    }
  } catch (error) {
    console.error("Lỗi lấy danh sách cuộc trò chuyện:", error);
  }
}

// Hiển thị danh sách cuộc trò chuyện
function displayConversations(conversationsData) {
  conversationsList.innerHTML = "";
  conversations = {};

  if (conversationsData.length === 0) {
    conversationsList.innerHTML = "<p>Chưa có cuộc trò chuyện</p>";
    return;
  }

  conversationsData.forEach((conversation) => {
    conversations[conversation.id] = conversation;

    const otherUser = conversation.participants.find(
      (p) => p.userId !== currentUser.userId
    );

    const conversationItem = document.createElement("div");
    conversationItem.className = "conversation-item";
    conversationItem.textContent = otherUser.displayName || otherUser.phone;
    conversationItem.dataset.conversationId = conversation.id;

    conversationItem.addEventListener("click", () => {
      loadConversation(conversation, otherUser);
    });

    conversationsList.appendChild(conversationItem);
  });
}

// Bắt đầu cuộc trò chuyện mới
async function startChat(user) {
  console.log("Bắt đầu chat với user:", user);

  // Kiểm tra xem đã có cuộc trò chuyện với người này chưa
  const existingConversation = Object.values(conversations).find((conv) =>
    conv.participants.some((p) => p.userId === user.userId)
  );

  if (existingConversation) {
    console.log("Đã có cuộc trò chuyện:", existingConversation);
    const otherUser = existingConversation.participants.find(
      (p) => p.userId !== currentUser.userId
    );
    loadConversation(existingConversation, otherUser);
    return;
  }

  console.log("Tạo cuộc trò chuyện mới với user:", user);
  // Tạo cuộc trò chuyện mới bằng API endpoint mới
  try {
    // Thêm dấu / ở đầu URL
    const response = await fetch(`/api/conversations/user/${user.userId}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
    });

    if (response.ok) {
      const conversation = await response.json();
      console.log("Cuộc trò chuyện mới được tạo:", conversation);
      conversations[conversation.id] = conversation;

      // Thêm vào danh sách cuộc trò chuyện
      const conversationItem = document.createElement("div");
      conversationItem.className = "conversation-item";
      conversationItem.textContent = user.displayName || user.phone;
      conversationItem.dataset.conversationId = conversation.id;

      conversationItem.addEventListener("click", () => {
        loadConversation(conversation, user);
      });

      conversationsList.appendChild(conversationItem);

      // Tải cuộc trò chuyện
      loadConversation(conversation, user);
    } else {
      const errorText = await response.text();
      console.error("Lỗi khi tạo cuộc trò chuyện:", response.status, errorText);
      try {
        const errorJson = JSON.parse(errorText);
        console.error("Chi tiết lỗi:", errorJson);
      } catch (e) {
        // Nếu không phải JSON, hiển thị text gốc
      }
    }
  } catch (error) {
    console.error("Lỗi tạo cuộc trò chuyện:", error);
  }
}

// Tải cuộc trò chuyện
async function loadConversation(conversation, user) {
  currentChat = {
    conversationId: conversation.id,
    user: user,
  };

  // Đăng ký nhận tin nhắn từ cuộc trò chuyện này
  subscribeToConversation(conversation.id);

  // Cập nhật UI
  chatWithSpan.textContent = user.displayName || user.phone;
  messageInput.disabled = false;
  sendBtn.disabled = false;

  // Đánh dấu cuộc trò chuyện đang hoạt động
  document.querySelectorAll(".conversation-item").forEach((item) => {
    item.classList.remove("active-chat");
  });

  const activeItem = document.querySelector(
    `.conversation-item[data-conversation-id="${conversation.id}"]`
  );
  if (activeItem) {
    activeItem.classList.add("active-chat");
  }

  // Lấy tin nhắn cũ
  try {
    const response = await fetch(
      `/api/conversations/${conversation.id}/messages`,
      {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      }
    );

    if (response.ok) {
      const messages = await response.json();
      displayMessages(messages);
    }
  } catch (error) {
    console.error("Lỗi lấy tin nhắn:", error);
  }
}

// Hiển thị tin nhắn
function displayMessages(messages) {
  messagesContainer.innerHTML = "";

  if (messages.length === 0) {
    const emptyMessage = document.createElement("p");
    emptyMessage.className = "empty-message";
    emptyMessage.textContent = "Chưa có tin nhắn. Hãy bắt đầu cuộc trò chuyện!";
    messagesContainer.appendChild(emptyMessage);
    return;
  }

  messages.forEach((message) => {
    const messageElement = document.createElement("div");
    messageElement.className = `message ${
      message.senderId === currentUser.userId
        ? "message-sent"
        : "message-received"
    }`;

    const messageContent = document.createElement("div");
    messageContent.className = "message-content";
    messageContent.textContent = message.content;

    const messageInfo = document.createElement("div");
    messageInfo.className = "message-info";

    // Format thời gian
    const messageTime = new Date(message.createdAt);
    messageInfo.textContent = messageTime.toLocaleString();

    messageElement.appendChild(messageContent);
    messageElement.appendChild(messageInfo);
    messagesContainer.appendChild(messageElement);
  });

  // Cuộn xuống tin nhắn mới nhất
  messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

// Gửi tin nhắn
async function sendMessage() {
  const content = messageInput.value.trim();

  if (!content || !currentChat) {
    return;
  }

  try {
    const response = await fetch(
      `/api/conversations/${currentChat.conversationId}/messages`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ content }),
      }
    );

    if (response.ok) {
      messageInput.value = "";

      // Tin nhắn sẽ được cập nhật thông qua WebSocket
    }
  } catch (error) {
    console.error("Lỗi gửi tin nhắn:", error);
  }
}

// Xử lý tin nhắn đến
function handleIncomingMessage(message) {
  // Nếu tin nhắn thuộc về cuộc trò chuyện hiện tại, hiển thị ngay
  if (currentChat && message.conversationId === currentChat.conversationId) {
    const messageElement = document.createElement("div");
    messageElement.className = `message ${
      message.senderId === currentUser.userId
        ? "message-sent"
        : "message-received"
    }`;

    const messageContent = document.createElement("div");
    messageContent.className = "message-content";
    messageContent.textContent = message.content;

    const messageInfo = document.createElement("div");
    messageInfo.className = "message-info";

    // Format thời gian
    const messageTime = new Date(message.createdAt);
    messageInfo.textContent = messageTime.toLocaleString();

    messageElement.appendChild(messageContent);
    messageElement.appendChild(messageInfo);
    messagesContainer.appendChild(messageElement);

    // Cuộn xuống tin nhắn mới nhất
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
  }

  // Cập nhật danh sách cuộc trò chuyện nếu cần
  fetchConversations();
}

// Đăng xuất
function logout() {
  if (stompClient) {
    stompClient.disconnect();
  }

  localStorage.removeItem("chatToken");
  currentUser = null;
  currentChat = null;
  token = null;
  conversations = {};

  loginSection.style.display = "block";
  chatSection.style.display = "none";
  document.getElementById("phone").value = "";
  document.getElementById("password").value = "";
  loginStatus.textContent = "";
}
