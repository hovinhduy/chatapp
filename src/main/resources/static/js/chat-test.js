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
const friendRequestsList = document.getElementById("friend-requests-list");
const pendingFriendsList = document.getElementById("pending-friends-list");

// Sự kiện khi trang được tải
document.addEventListener("DOMContentLoaded", () => {
  // Kiểm tra xem đã có token trong localStorage chưa
  token = localStorage.getItem("chatToken");
  if (token) {
    fetchCurrentUser();
  }

  // Thêm sự kiện tìm kiếm người dùng
  const searchBtn = document.getElementById("search-btn");
  searchBtn.addEventListener("click", searchUsers);

  // Cho phép tìm kiếm bằng cách nhấn Enter
  const searchInput = document.getElementById("search-user");
  searchInput.addEventListener("keyup", function (event) {
    if (event.key === "Enter") {
      searchUsers();
    }
  });
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

    if (response.ok && data.success) {
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

    const data = await response.json();

    if (response.ok && data.success) {
      currentUser = data.payload;
      loggedUserSpan.textContent = currentUser.displayName || currentUser.phone;

      loginSection.style.display = "none";
      chatSection.style.display = "block";

      // Kết nối WebSocket sau khi đăng nhập thành công
      connectWebSocket();

      // Tải dữ liệu ban đầu
      fetchInitialData();
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

        // Đăng ký nhận thông báo bạn bè
        subscribeFriendRequests();
        subscribeFriendUpdates();
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
      `/queue/conversation/${conversationId}`,
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

// Đăng ký nhận thông báo lời mời kết bạn
function subscribeFriendRequests() {
  if (stompClient && stompClient.connected && currentUser) {
    stompClient.subscribe(
      `/queue/user/${currentUser.userId}/friend-requests`,
      function (message) {
        const friendRequest = JSON.parse(message.body);
        console.log("Nhận lời mời kết bạn mới:", friendRequest);
        showNotification(
          `Bạn có lời mời kết bạn mới từ ${friendRequest.user1.displayName}`
        );
        fetchPendingFriendRequests(); // Cập nhật danh sách lời mời
      }
    );
    console.log(`Đã đăng ký nhận thông báo lời mời kết bạn`);
  }
}

// Đăng ký nhận cập nhật về trạng thái bạn bè
function subscribeFriendUpdates() {
  if (stompClient && stompClient.connected && currentUser) {
    stompClient.subscribe(
      `/queue/user/${currentUser.userId}/friend-updates`,
      function (message) {
        const friendUpdate = JSON.parse(message.body);
        console.log("Nhận cập nhật bạn bè:", friendUpdate);

        // Xử lý các loại cập nhật dựa vào trạng thái
        switch (friendUpdate.status) {
          case "ACCEPTED":
            showNotification(
              `${getOtherUserName(
                friendUpdate
              )} đã chấp nhận lời mời kết bạn của bạn`
            );
            fetchFriends(); // Cập nhật danh sách bạn bè
            fetchSentFriendRequests(); // Cập nhật danh sách lời mời đã gửi
            break;
          case "REJECTED":
            showNotification(
              `${getOtherUserName(
                friendUpdate
              )} đã từ chối lời mời kết bạn của bạn`
            );
            fetchSentFriendRequests(); // Cập nhật danh sách lời mời đã gửi
            break;
          case "BLOCKED":
            showNotification(`${getOtherUserName(friendUpdate)} đã chặn bạn`);
            fetchFriends(); // Cập nhật danh sách bạn bè
            break;
          case "PENDING":
            // Lời mời được thu hồi hoặc đã bị xóa
            fetchPendingFriendRequests();
            fetchFriends();
            break;
          default:
            // Cập nhật tất cả để đảm bảo UI đồng bộ
            fetchFriends();
            fetchPendingFriendRequests();
            fetchSentFriendRequests();
        }
      }
    );
    console.log(`Đã đăng ký nhận cập nhật trạng thái bạn bè`);
  }
}

// Lấy tên của user khác trong mối quan hệ bạn bè
function getOtherUserName(friendRelation) {
  if (currentUser) {
    if (friendRelation.user1.userId === currentUser.userId) {
      return friendRelation.user2.displayName;
    } else {
      return friendRelation.user1.displayName;
    }
  }
  return "";
}

// Hiển thị thông báo
function showNotification(message) {
  // Tạo và hiển thị thông báo
  const notification = document.createElement("div");
  notification.className = "notification";
  notification.textContent = message;
  document.body.appendChild(notification);

  // Xóa thông báo sau 5 giây
  setTimeout(() => {
    notification.remove();
  }, 5000);
}

// Lấy danh sách bạn bè
async function fetchFriends() {
  try {
    const response = await fetch("/api/friends", {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    const data = await response.json();

    if (response.ok && data.success) {
      displayFriends(data.payload);
    }
  } catch (error) {
    console.error("Lỗi khi lấy danh sách bạn bè:", error);
  }
}

// Hiển thị danh sách bạn bè
function displayFriends(friends) {
  friendsList.innerHTML = "";
  if (friends.length === 0) {
    const emptyMessage = document.createElement("div");
    emptyMessage.className = "empty-list";
    emptyMessage.textContent = "Bạn chưa có bạn bè";
    friendsList.appendChild(emptyMessage);
    return;
  }

  friends.forEach((friend) => {
    const otherUser =
      friend.user1.userId === currentUser.userId ? friend.user2 : friend.user1;
    const friendItem = document.createElement("div");
    friendItem.className = "friend-item";
    friendItem.dataset.userId = otherUser.userId;

    const nameSpan = document.createElement("span");
    nameSpan.textContent = otherUser.displayName || otherUser.phone;
    friendItem.appendChild(nameSpan);

    const actionsDiv = document.createElement("div");
    actionsDiv.className = "friend-actions";

    const chatBtn = document.createElement("button");
    chatBtn.textContent = "Nhắn tin";
    chatBtn.addEventListener("click", () => startChat(otherUser));
    actionsDiv.appendChild(chatBtn);

    const unfriendBtn = document.createElement("button");
    unfriendBtn.textContent = "Xóa bạn";
    unfriendBtn.className = "btn-danger";
    unfriendBtn.addEventListener("click", () => deleteFriend(friend.id));
    actionsDiv.appendChild(unfriendBtn);

    friendItem.appendChild(actionsDiv);
    friendsList.appendChild(friendItem);
  });
}

// Xóa bạn bè
async function deleteFriend(friendshipId) {
  if (!confirm("Bạn có chắc muốn xóa người bạn này không?")) {
    return;
  }

  try {
    const response = await fetch(`/api/friends/${friendshipId}`, {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    const data = await response.json();

    if (response.ok && data.success) {
      fetchFriends();
      showNotification("Đã xóa bạn bè thành công");
    } else {
      console.error("Lỗi khi xóa bạn bè:", data.message);
    }
  } catch (error) {
    console.error("Lỗi khi xóa bạn bè:", error);
  }
}

// Lấy danh sách lời mời kết bạn đang chờ
async function fetchPendingFriendRequests() {
  try {
    const response = await fetch("/api/friends/pending", {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    const data = await response.json();

    if (response.ok && data.success) {
      displayPendingFriendRequests(data.payload);
    }
  } catch (error) {
    console.error("Lỗi khi lấy danh sách lời mời kết bạn:", error);
  }
}

// Hiển thị danh sách lời mời kết bạn đang chờ
function displayPendingFriendRequests(requests) {
  friendRequestsList.innerHTML = "";

  if (requests.length === 0) {
    const emptyMessage = document.createElement("div");
    emptyMessage.className = "empty-list";
    emptyMessage.textContent = "Không có lời mời kết bạn nào";
    friendRequestsList.appendChild(emptyMessage);
    return;
  }

  requests.forEach((request) => {
    const requestItem = document.createElement("div");
    requestItem.className = "friend-request-item";
    requestItem.dataset.userId = request.user1.userId;
    requestItem.dataset.requestId = request.id;

    const nameSpan = document.createElement("span");
    nameSpan.textContent = request.user1.displayName || request.user1.phone;
    requestItem.appendChild(nameSpan);

    const actionsDiv = document.createElement("div");
    actionsDiv.className = "friend-actions";

    const acceptBtn = document.createElement("button");
    acceptBtn.textContent = "Chấp nhận";
    acceptBtn.className = "btn-success";
    acceptBtn.addEventListener("click", () => acceptFriendRequest(request.id));
    actionsDiv.appendChild(acceptBtn);

    const rejectBtn = document.createElement("button");
    rejectBtn.textContent = "Từ chối";
    rejectBtn.className = "btn-danger";
    rejectBtn.addEventListener("click", () => rejectFriendRequest(request.id));
    actionsDiv.appendChild(rejectBtn);

    requestItem.appendChild(actionsDiv);
    friendRequestsList.appendChild(requestItem);
  });
}

// Chấp nhận lời mời kết bạn
async function acceptFriendRequest(friendshipId) {
  try {
    const response = await fetch(`/api/friends/accept/${friendshipId}`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    const data = await response.json();

    if (response.ok && data.success) {
      fetchPendingFriendRequests();
      fetchFriends();
      showNotification("Đã chấp nhận lời mời kết bạn");
    } else {
      console.error("Lỗi khi chấp nhận lời mời kết bạn:", data.message);
    }
  } catch (error) {
    console.error("Lỗi khi chấp nhận lời mời kết bạn:", error);
  }
}

// Từ chối lời mời kết bạn
async function rejectFriendRequest(friendshipId) {
  try {
    const response = await fetch(`/api/friends/reject/${friendshipId}`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    const data = await response.json();

    if (response.ok && data.success) {
      fetchPendingFriendRequests();
      showNotification("Đã từ chối lời mời kết bạn");
    } else {
      console.error("Lỗi khi từ chối lời mời kết bạn:", data.message);
    }
  } catch (error) {
    console.error("Lỗi khi từ chối lời mời kết bạn:", error);
  }
}

// Lấy danh sách lời mời kết bạn đã gửi
async function fetchSentFriendRequests() {
  try {
    const response = await fetch("/api/friends/sent", {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    const data = await response.json();

    if (response.ok && data.success) {
      displaySentFriendRequests(data.payload);
    }
  } catch (error) {
    console.error("Lỗi khi lấy danh sách lời mời đã gửi:", error);
  }
}

// Hiển thị danh sách lời mời kết bạn đã gửi
function displaySentFriendRequests(requests) {
  pendingFriendsList.innerHTML = "";

  if (requests.length === 0) {
    const emptyMessage = document.createElement("div");
    emptyMessage.className = "empty-list";
    emptyMessage.textContent = "Bạn chưa gửi lời mời kết bạn nào";
    pendingFriendsList.appendChild(emptyMessage);
    return;
  }

  requests.forEach((request) => {
    const requestItem = document.createElement("div");
    requestItem.className = "sent-request-item";
    requestItem.dataset.userId = request.user2.userId;

    const nameSpan = document.createElement("span");
    nameSpan.textContent = request.user2.displayName || request.user2.phone;
    requestItem.appendChild(nameSpan);

    const withdrawBtn = document.createElement("button");
    withdrawBtn.textContent = "Thu hồi";
    withdrawBtn.className = "btn-danger";
    withdrawBtn.addEventListener("click", () =>
      withdrawFriendRequest(request.id)
    );
    requestItem.appendChild(withdrawBtn);

    pendingFriendsList.appendChild(requestItem);
  });
}

// Thu hồi lời mời kết bạn
async function withdrawFriendRequest(friendshipId) {
  try {
    const response = await fetch(`/api/friends/withdraw/${friendshipId}`, {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    const data = await response.json();

    if (response.ok && data.success) {
      fetchSentFriendRequests();
      showNotification("Đã thu hồi lời mời kết bạn");
    } else {
      console.error("Lỗi khi thu hồi lời mời kết bạn:", data.message);
    }
  } catch (error) {
    console.error("Lỗi khi thu hồi lời mời kết bạn:", error);
  }
}

// Gửi lời mời kết bạn
async function sendFriendRequest(userId) {
  try {
    const response = await fetch(`/api/friends/request/${userId}`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    const data = await response.json();

    if (response.ok && data.success) {
      fetchSentFriendRequests();
      showNotification("Đã gửi lời mời kết bạn");
    } else {
      console.error("Lỗi khi gửi lời mời kết bạn:", data.message);
    }
  } catch (error) {
    console.error("Lỗi khi gửi lời mời kết bạn:", error);
  }
}

// Cập nhật tất cả thông tin ban đầu
async function fetchInitialData() {
  await fetchFriends();
  await fetchPendingFriendRequests();
  await fetchSentFriendRequests();
  await fetchConversations();
}

// Thêm hàm mới để bắt đầu chat với người dùng
async function startChatWithUser(user) {
  try {
    const response = await fetch(`/api/conversations/user/${user.userId}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
    });

    const data = await response.json();

    if (response.ok && data.success) {
      selectConversation(data.payload);
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

    const data = await response.json();

    if (response.ok && data.success) {
      displayConversations(data.payload);
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
  try {
    const response = await fetch(`/api/conversations/user/${user.userId}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
    });

    const data = await response.json();

    if (response.ok && data.success) {
      const conversation = data.payload;
      console.log("Cuộc trò chuyện mới được tạo:", conversation);
      conversations[conversation.id] = conversation;

      const conversationItem = document.createElement("div");
      conversationItem.className = "conversation-item";
      conversationItem.textContent = user.displayName || user.phone;
      conversationItem.dataset.conversationId = conversation.id;

      conversationItem.addEventListener("click", () => {
        loadConversation(conversation, user);
      });

      conversationsList.appendChild(conversationItem);
      loadConversation(conversation, user);
    } else {
      console.error("Lỗi khi tạo cuộc trò chuyện:", data.message);
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

  subscribeToConversation(conversation.id);

  chatWithSpan.textContent = user.displayName || user.phone;
  messageInput.disabled = false;
  sendBtn.disabled = false;

  document.querySelectorAll(".conversation-item").forEach((item) => {
    item.classList.remove("active-chat");
  });

  const activeItem = document.querySelector(
    `.conversation-item[data-conversation-id="${conversation.id}"]`
  );
  if (activeItem) {
    activeItem.classList.add("active-chat");
  }

  try {
    const response = await fetch(
      `/api/conversations/${conversation.id}/messages`,
      {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      }
    );

    const data = await response.json();

    if (response.ok && data.success) {
      displayMessages(data.payload);
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

    const data = await response.json();

    if (response.ok && data.success) {
      messageInput.value = "";
      // Tin nhắn sẽ được cập nhật thông qua WebSocket
    } else {
      console.error("Lỗi gửi tin nhắn:", data.message);
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

  // KHÔNG gọi fetchConversations() ở đây để tránh load tin nhắn 2 lần
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

// Tìm kiếm người dùng
async function searchUsers() {
  const searchInput = document.getElementById("search-user");
  const searchTerm = searchInput.value.trim();
  const searchResults = document.getElementById("search-results");

  if (!searchTerm) {
    searchResults.innerHTML =
      "<div class='empty-list'>Vui lòng nhập từ khóa tìm kiếm</div>";
    return;
  }

  try {
    const response = await fetch(
      `/api/users/by-phone?phone=${encodeURIComponent(searchTerm)}`,
      {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      }
    );

    const data = await response.json();

    if (response.ok && data.success) {
      // Xử lý trường hợp data.payload là một đối tượng đơn lẻ (không phải mảng)
      if (data.payload && !Array.isArray(data.payload)) {
        displaySearchResults([data.payload]); // Chuyển đối tượng đơn lẻ thành mảng
      } else {
        displaySearchResults(data.payload);
      }
    } else {
      searchResults.innerHTML = `<div class='empty-list'>Lỗi: ${data.message}</div>`;
    }
  } catch (error) {
    console.error("Lỗi khi tìm kiếm người dùng:", error);
    searchResults.innerHTML =
      "<div class='empty-list'>Lỗi kết nối máy chủ</div>";
  }
}

// Hiển thị kết quả tìm kiếm người dùng
function displaySearchResults(users) {
  const searchResults = document.getElementById("search-results");
  searchResults.innerHTML = "";

  if (!users || users.length === 0) {
    searchResults.innerHTML =
      "<div class='empty-list'>Không tìm thấy người dùng</div>";
    return;
  }

  // Lấy danh sách bạn bè hiện tại để kiểm tra
  fetchFriends().then(() => {
    fetchPendingFriendRequests().then(() => {
      fetchSentFriendRequests().then(() => {
        users.forEach((user) => {
          // Bỏ qua người dùng hiện tại
          if (user.userId === currentUser.userId) {
            return;
          }

          const userItem = document.createElement("div");
          userItem.className = "user-item";

          const nameSpan = document.createElement("span");
          nameSpan.textContent = user.displayName || user.phone;
          userItem.appendChild(nameSpan);

          const actionsDiv = document.createElement("div");
          actionsDiv.className = "friend-actions";

          // Kiểm tra xem người dùng này đã là bạn bè chưa
          const isFriend = checkIfFriend(user.userId);
          // Kiểm tra xem đã gửi lời mời kết bạn chưa
          const hasSentRequest = checkIfSentRequest(user.userId);
          // Kiểm tra xem đã nhận lời mời kết bạn từ người này chưa
          const hasReceivedRequest = checkIfReceivedRequest(user.userId);

          if (isFriend) {
            // Nếu đã là bạn bè
            const chatBtn = document.createElement("button");
            chatBtn.textContent = "Nhắn tin";
            chatBtn.addEventListener("click", () => startChat(user));
            actionsDiv.appendChild(chatBtn);
          } else if (hasSentRequest) {
            // Nếu đã gửi lời mời kết bạn
            const pendingBtn = document.createElement("button");
            pendingBtn.textContent = "Đã gửi lời mời";
            pendingBtn.disabled = true;
            actionsDiv.appendChild(pendingBtn);
          } else if (hasReceivedRequest) {
            // Nếu đã nhận lời mời kết bạn
            const acceptBtn = document.createElement("button");
            acceptBtn.textContent = "Chấp nhận";
            acceptBtn.className = "btn-success";
            acceptBtn.addEventListener("click", () => {
              // Cần tìm ID của lời mời
              const request = findFriendRequest(user.userId);
              if (request) {
                acceptFriendRequest(request.id);
              }
            });
            actionsDiv.appendChild(acceptBtn);
          } else {
            // Nếu chưa có mối quan hệ bạn bè
            const addBtn = document.createElement("button");
            addBtn.textContent = "Kết bạn";
            addBtn.addEventListener("click", () =>
              sendFriendRequest(user.userId)
            );
            actionsDiv.appendChild(addBtn);
          }

          userItem.appendChild(actionsDiv);
          searchResults.appendChild(userItem);
        });
      });
    });
  });
}

// Kiểm tra xem một người dùng đã là bạn bè chưa
function checkIfFriend(userId) {
  const friendsList = document.getElementById("friends-list");
  const friends = Array.from(friendsList.querySelectorAll(".friend-item"));

  // Duyệt qua danh sách bạn bè để kiểm tra
  for (const friend of friends) {
    const friendUserId = friend.dataset.userId;
    if (friendUserId == userId) {
      return true;
    }
  }

  return false;
}

// Kiểm tra xem đã gửi lời mời kết bạn đến người dùng này chưa
function checkIfSentRequest(userId) {
  const pendingList = document.getElementById("pending-friends-list");
  const sentRequests = Array.from(
    pendingList.querySelectorAll(".sent-request-item")
  );

  for (const request of sentRequests) {
    const requestUserId = request.dataset.userId;
    if (requestUserId == userId) {
      return true;
    }
  }

  return false;
}

// Kiểm tra xem đã nhận lời mời kết bạn từ người dùng này chưa
function checkIfReceivedRequest(userId) {
  const requestsList = document.getElementById("friend-requests-list");
  const receivedRequests = Array.from(
    requestsList.querySelectorAll(".friend-request-item")
  );

  for (const request of receivedRequests) {
    const requestUserId = request.dataset.userId;
    if (requestUserId == userId) {
      return true;
    }
  }

  return false;
}

// Tìm lời mời kết bạn từ một người dùng cụ thể
function findFriendRequest(userId) {
  const requestsList = document.getElementById("friend-requests-list");
  const receivedRequests = Array.from(
    requestsList.querySelectorAll(".friend-request-item")
  );

  for (const request of receivedRequests) {
    if (request.dataset.userId == userId) {
      return { id: request.dataset.requestId };
    }
  }

  return null;
}
