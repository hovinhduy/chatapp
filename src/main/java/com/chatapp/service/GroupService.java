package com.chatapp.service;

import com.chatapp.dto.request.GroupDto;
import com.chatapp.dto.request.GroupMemberDto;
import com.chatapp.dto.request.UserDto;
import com.chatapp.dto.request.GroupCreateDto;
import com.chatapp.dto.request.ConversationDto;
import com.chatapp.enums.FriendStatus;
import com.chatapp.enums.GroupRole;
import com.chatapp.exception.BadRequestException;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.exception.UnauthorizedException;
import com.chatapp.model.Group;
import com.chatapp.model.GroupMember;
import com.chatapp.model.User;
import com.chatapp.model.Conversation;
import com.chatapp.repository.FriendRepository;
import com.chatapp.repository.GroupMemberRepository;
import com.chatapp.repository.GroupRepository;
import com.chatapp.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service xử lý các thao tác liên quan đến nhóm chat và thành viên nhóm
 */
@Service
public class GroupService {

        private final GroupRepository groupRepository;
        private final GroupMemberRepository groupMemberRepository;
        private final UserRepository userRepository;
        private final FriendRepository friendRepository;
        private final ConversationService conversationService;
        private final SimpMessagingTemplate messagingTemplate;

        /**
         * Constructor để dependency injection
         * 
         * @param groupRepository       Repository xử lý thao tác với database của Group
         * @param groupMemberRepository Repository xử lý thao tác với database của
         *                              GroupMember
         * @param userRepository        Repository xử lý thao tác với database của User
         * @param friendRepository      Repository xử lý thao tác với database của
         *                              Friend
         * @param conversationService   Service xử lý các thao tác liên quan đến cuộc
         *                              trò chuyện
         * @param messagingTemplate     Template để gửi thông báo WebSocket realtime
         */
        public GroupService(GroupRepository groupRepository, GroupMemberRepository groupMemberRepository,
                        UserRepository userRepository, FriendRepository friendRepository,
                        ConversationService conversationService, SimpMessagingTemplate messagingTemplate) {
                this.groupRepository = groupRepository;
                this.groupMemberRepository = groupMemberRepository;
                this.userRepository = userRepository;
                this.friendRepository = friendRepository;
                this.conversationService = conversationService;
                this.messagingTemplate = messagingTemplate;
        }

        /**
         * Tạo mới nhóm chat từ GroupCreateDto
         * 
         * @param groupCreateDto Đối tượng chứa thông tin nhóm cần tạo (đã được rút gọn)
         * @param creatorId      ID của người tạo nhóm
         * @return GroupDto Thông tin nhóm đã được tạo
         * @throws ResourceNotFoundException Nếu không tìm thấy người tạo nhóm
         */
        @Transactional
        public GroupDto createGroup(GroupCreateDto groupCreateDto, Long creatorId) {
                // Kiểm tra xem có ít nhất 2 thành viên khác được thêm vào nhóm
                if (groupCreateDto.getMemberIds() == null || groupCreateDto.getMemberIds().size() < 2) {
                        throw new BadRequestException("Nhóm phải có ít nhất 2 thành viên ngoài người tạo");
                }

                User creator = userRepository.findById(creatorId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy người dùng với id: " + creatorId));

                Group group = new Group();
                group.setName(groupCreateDto.getName());
                group.setAvatarUrl(groupCreateDto.getAvatarUrl());

                Group savedGroup = groupRepository.save(group);

                // Thêm người tạo làm trưởng nhóm
                GroupMember groupMember = new GroupMember();
                groupMember.setGroup(savedGroup);
                groupMember.setUser(creator);
                groupMember.setRole(GroupRole.LEADER); // Trưởng nhóm
                groupMemberRepository.save(groupMember);

                // Danh sách thành viên Group
                List<GroupMemberDto> groupMembers = new ArrayList<>();

                // Thêm chi tiết trưởng nhóm vào danh sách thành viên để trả về
                GroupMemberDto leaderMemberDto = new GroupMemberDto();
                leaderMemberDto.setId(groupMember.getId());
                leaderMemberDto.setGroupId(savedGroup.getGroupId());
                UserDto leaderUserDto = new UserDto();
                leaderUserDto.setUserId(creator.getUserId());
                leaderUserDto.setDisplayName(creator.getDisplayName());
                leaderUserDto.setPhone(creator.getPhone());
                leaderUserDto.setAvatarUrl(creator.getAvatarUrl());
                leaderMemberDto.setUser(leaderUserDto);
                leaderMemberDto.setRole(GroupRole.LEADER);
                groupMembers.add(leaderMemberDto);

                // Danh sách ID của tất cả thành viên, bao gồm người tạo
                List<Long> allMemberIds = new ArrayList<>();
                allMemberIds.add(creatorId);

                // Thêm các thành viên khác từ danh sách ID
                for (Long memberId : groupCreateDto.getMemberIds()) {
                        // Bỏ qua nếu là ID của người tạo
                        if (memberId.equals(creatorId)) {
                                continue;
                        }

                        User member = userRepository.findById(memberId)
                                        .orElseThrow(() -> new ResourceNotFoundException(
                                                        "Không tìm thấy người dùng với id: " + memberId));

                        // Kiểm tra xem người được thêm có phải là bạn bè không
                        boolean isFriend = friendRepository.findByUsers(creator, member)
                                        .map(friend -> friend.getStatus() == FriendStatus.ACCEPTED)
                                        .orElse(false);

                        if (!isFriend) {
                                throw new BadRequestException(
                                                "Người dùng với id " + memberId + " không phải là bạn bè của bạn");
                        }

                        GroupMember newMember = new GroupMember();
                        newMember.setGroup(savedGroup);
                        newMember.setUser(member);
                        newMember.setRole(GroupRole.MEMBER); // Thành viên thường
                        GroupMember savedMember = groupMemberRepository.save(newMember);

                        // Thêm ID thành viên vào danh sách
                        allMemberIds.add(memberId);

                        // Thêm chi tiết thành viên vào danh sách thành viên để trả về
                        GroupMemberDto memberDto = new GroupMemberDto();
                        memberDto.setId(savedMember.getId());
                        memberDto.setGroupId(savedGroup.getGroupId());
                        UserDto userDto = new UserDto();
                        userDto.setUserId(member.getUserId());
                        userDto.setDisplayName(member.getDisplayName());
                        userDto.setPhone(member.getPhone());
                        userDto.setAvatarUrl(member.getAvatarUrl());
                        memberDto.setUser(userDto);
                        memberDto.setRole(savedMember.getRole());
                        groupMembers.add(memberDto);
                }

                // Tạo cuộc trò chuyện nhóm sau khi tạo nhóm thành công
                ConversationDto conversationDto = conversationService.createConversation(creatorId, allMemberIds);

                // Liên kết conversation với group
                Conversation conversation = conversationService.getConversationById(conversationDto.getId());
                savedGroup.setConversation(conversation);
                groupRepository.save(savedGroup);

                // Gửi thông báo tạo nhóm mới
                conversationService.sendGroupCreatedNotification(conversationDto.getId(),
                                creator.getDisplayName(), savedGroup.getName());

                // Gửi thông báo realtime đến tất cả thành viên về việc tạo nhóm mới
                sendGroupCreatedNotificationToMembers(savedGroup, allMemberIds, creator.getDisplayName());

                GroupDto result = new GroupDto();
                result.setGroupId(savedGroup.getGroupId());
                result.setName(savedGroup.getName());
                result.setAvatarUrl(savedGroup.getAvatarUrl());
                result.setCreatedAt(savedGroup.getCreatedAt());
                result.setMembers(groupMembers);
                result.setConversationId(conversationDto.getId()); // Lưu ID cuộc trò chuyện

                return result;
        }

        /**
         * Tạo mới nhóm chat từ GroupDto (phương thức cũ - để tương thích ngược)
         * 
         * @param groupDto  Đối tượng chứa thông tin nhóm cần tạo
         * @param creatorId ID của người tạo nhóm
         * @return GroupDto Thông tin nhóm đã được tạo
         * @throws ResourceNotFoundException Nếu không tìm thấy người tạo nhóm
         * @deprecated Sử dụng {@link #createGroup(GroupCreateDto, Long)} thay thế
         */
        @Transactional
        @Deprecated
        public GroupDto createGroup(GroupDto groupDto, Long creatorId) {
                // Tạo đối tượng GroupCreateDto từ GroupDto
                GroupCreateDto groupCreateDto = new GroupCreateDto();
                groupCreateDto.setName(groupDto.getName());
                groupCreateDto.setAvatarUrl(groupDto.getAvatarUrl());

                // Lấy danh sách user ID từ các thành viên (nếu có)
                List<Long> memberIds = new ArrayList<>();
                if (groupDto.getMembers() != null) {
                        for (GroupMemberDto memberDto : groupDto.getMembers()) {
                                if (memberDto.getUser() != null && memberDto.getUser().getUserId() != null) {
                                        memberIds.add(memberDto.getUser().getUserId());
                                }
                        }
                }
                groupCreateDto.setMemberIds(memberIds);

                // Gọi phương thức mới
                return createGroup(groupCreateDto, creatorId);
        }

        /**
         * Lấy thông tin của nhóm theo ID
         * 
         * @param groupId ID của nhóm cần tìm
         * @return GroupDto Thông tin của nhóm
         * @throws ResourceNotFoundException Nếu không tìm thấy nhóm
         */
        public GroupDto getGroupById(Long groupId) {
                Group group = groupRepository.findById(groupId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhóm với id: " + groupId));
                return mapToDto(group);
        }

        /**
         * Lấy danh sách các nhóm mà người dùng là thành viên
         * 
         * @param userId ID của người dùng
         * @return List<GroupDto> Danh sách các nhóm
         */
        public List<GroupDto> getGroupsByUserId(Long userId) {
                return groupRepository.findGroupsByUserId(userId).stream()
                                .map(this::mapToDto)
                                .collect(Collectors.toList());
        }

        /**
         * Tìm kiếm các nhóm theo tên mà người dùng đang tham gia
         * 
         * @param userId ID của người dùng
         * @param name   Tên nhóm cần tìm kiếm (tìm kiếm mờ, không phân biệt hoa thường)
         * @return List<GroupDto> Danh sách các nhóm phù hợp với điều kiện tìm kiếm
         */
        public List<GroupDto> searchGroupsByName(Long userId, String name) {
                return groupRepository.findGroupsByUserIdAndNameContainingIgnoreCase(userId, name).stream()
                                .map(this::mapToDto)
                                .collect(Collectors.toList());
        }

        /**
         * Cập nhật thông tin nhóm
         * 
         * @param groupId  ID của nhóm cần cập nhật
         * @param groupDto Đối tượng chứa thông tin cần cập nhật
         * @param userId   ID của người dùng thực hiện cập nhật
         * @return GroupDto Thông tin nhóm sau khi cập nhật
         * @throws ResourceNotFoundException Nếu không tìm thấy nhóm hoặc người dùng
         * @throws UnauthorizedException     Nếu người dùng không phải là admin của nhóm
         */
        @Transactional
        public GroupDto updateGroup(Long groupId, GroupDto groupDto, Long userId) {
                Group group = groupRepository.findById(groupId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhóm với id: " + groupId));

                // Check if user is admin
                GroupMember member = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(userId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Không tìm thấy người dùng với id: " + userId)))
                                .orElseThrow(() -> new UnauthorizedException(
                                                "Bạn không phải là thành viên của nhóm này"));

                if (!member.getRole().equals(GroupRole.LEADER)) {
                        throw new UnauthorizedException("Chỉ trưởng nhóm mới có thể cập nhật thông tin nhóm");
                }

                String oldName = group.getName();
                boolean nameChanged = false;
                boolean avatarChanged = false;

                if (groupDto.getName() != null && !groupDto.getName().equals(oldName)) {
                        group.setName(groupDto.getName());
                        nameChanged = true;
                }
                if (groupDto.getAvatarUrl() != null) {
                        group.setAvatarUrl(groupDto.getAvatarUrl());
                        avatarChanged = true;
                }

                Group updatedGroup = groupRepository.save(group);

                // Gửi thông báo cập nhật nhóm nếu có thay đổi
                if (group.getConversation() != null) {
                        String updaterName = member.getUser().getDisplayName();
                        if (nameChanged) {
                                conversationService.sendGroupUpdatedNotification(
                                                group.getConversation().getId(),
                                                updaterName,
                                                "name",
                                                groupDto.getName());
                        }
                        if (avatarChanged) {
                                conversationService.sendGroupUpdatedNotification(
                                                group.getConversation().getId(),
                                                updaterName,
                                                "avatar",
                                                null);
                        }
                }

                return mapToDto(updatedGroup);
        }

        /**
         * Thêm thành viên vào nhóm
         * 
         * @param groupId   ID của nhóm
         * @param userId    ID của người dùng cần thêm vào nhóm
         * @param managerId ID của người quản lý thêm thành viên
         * @throws ResourceNotFoundException Nếu không tìm thấy nhóm, người dùng hoặc
         *                                   người quản lý
         * @throws UnauthorizedException     Nếu người quản lý không phải là admin của
         *                                   nhóm
         * @throws BadRequestException       Nếu người dùng đã là thành viên của nhóm
         */
        @Transactional
        public void addMember(Long groupId, Long userId, Long managerId) {
                Group group = groupRepository.findById(groupId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhóm với id: " + groupId));

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy người dùng với id: " + userId));

                // Kiểm tra quyền quản lý
                GroupMember managerMember = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(managerId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Không tìm thấy người quản lý với id: " + managerId)))
                                .orElseThrow(() -> new UnauthorizedException(
                                                "Người quản lý không phải là thành viên của nhóm này"));

                if (!hasManagePermission(managerMember)) {
                        throw new UnauthorizedException("Chỉ trưởng nhóm hoặc phó nhóm mới có thể thêm thành viên");
                }

                // Check if user is already a member
                if (groupMemberRepository.existsByGroupAndUser(group, user)) {
                        throw new BadRequestException("Người dùng đã là thành viên của nhóm này");
                }

                // Thêm thành viên vào nhóm
                GroupMember groupMember = new GroupMember();
                groupMember.setGroup(group);
                groupMember.setUser(user);
                groupMember.setRole(GroupRole.MEMBER); // Thành viên thường
                groupMemberRepository.save(groupMember);

                // Thêm thành viên vào cuộc trò chuyện của nhóm (nếu có)
                if (group.getConversation() != null) {
                        // Tạo ConversationUser mới để thêm người dùng vào cuộc trò chuyện
                        conversationService.addUserToConversation(group.getConversation().getId(), userId);

                        // Gửi thông báo thêm thành viên
                        conversationService.sendGroupMemberAddedNotification(
                                        group.getConversation().getId(),
                                        user.getDisplayName(),
                                        managerMember.getUser().getDisplayName());
                }

                // Gửi thông báo realtime đến thành viên được thêm vào nhóm
                sendMemberAddedNotificationToNewMember(userId, group, user.getDisplayName(),
                                managerMember.getUser().getDisplayName());

                // Gửi thông báo realtime đến các thành viên hiện tại về việc có thành viên mới
                sendMemberAddedNotificationToExistingMembers(group, user.getDisplayName(),
                                managerMember.getUser().getDisplayName());
        }

        /**
         * Xóa thành viên khỏi nhóm
         * 
         * @param groupId   ID của nhóm
         * @param userId    ID của người dùng cần xóa khỏi nhóm
         * @param managerId ID của người quản lý thực hiện xóa thành viên
         * @throws ResourceNotFoundException Nếu không tìm thấy nhóm, người dùng hoặc
         *                                   người quản lý
         * @throws UnauthorizedException     Nếu người quản lý không phải là admin của
         *                                   nhóm
         * @throws BadRequestException       Nếu người dùng không phải là thành viên của
         *                                   nhóm
         */
        @Transactional
        public void removeMember(Long groupId, Long userId, Long managerId) {
                Group group = groupRepository.findById(groupId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhóm với id: " + groupId));

                // Kiểm tra quyền quản lý
                GroupMember managerMember = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(managerId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Không tìm thấy người quản lý với id: " + managerId)))
                                .orElseThrow(() -> new UnauthorizedException(
                                                "Người quản lý không phải là thành viên của nhóm này"));

                if (!hasManagePermission(managerMember)) {
                        throw new UnauthorizedException("Chỉ trưởng nhóm hoặc phó nhóm mới có thể xóa thành viên");
                }

                // Tìm thành viên cần xóa
                GroupMember memberToRemove = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(userId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Không tìm thấy người dùng với id: " + userId)))
                                .orElseThrow(() -> new BadRequestException(
                                                "Người dùng không phải là thành viên của nhóm này"));

                // Không thể xóa trưởng nhóm
                if (memberToRemove.getRole() == GroupRole.LEADER) {
                        throw new UnauthorizedException("Không thể xóa trưởng nhóm");
                }

                // Phó nhóm chỉ có thể xóa thành viên thường
                if (managerMember.getRole() == GroupRole.DEPUTY && memberToRemove.getRole() == GroupRole.DEPUTY) {
                        throw new UnauthorizedException("Phó nhóm không thể xóa phó nhóm khác");
                }

                // Lưu tên thành viên trước khi xóa
                String memberName = memberToRemove.getUser().getDisplayName();

                groupMemberRepository.delete(memberToRemove);

                // Xóa người dùng khỏi cuộc trò chuyện của nhóm (nếu có)
                if (group.getConversation() != null) {
                        conversationService.removeUserFromConversation(group.getConversation().getId(), userId);

                        // Gửi thông báo xóa thành viên
                        conversationService.sendGroupMemberRemovedNotification(
                                        group.getConversation().getId(),
                                        memberName,
                                        managerMember.getUser().getDisplayName());
                }

                // Gửi thông báo realtime đến thành viên bị xóa
                sendMemberRemovedNotificationToUser(userId, group, memberName,
                                managerMember.getUser().getDisplayName());

                // Gửi thông báo realtime đến các thành viên còn lại trong nhóm
                sendMemberRemovedNotificationToRemainingMembers(group, memberName,
                                managerMember.getUser().getDisplayName());
        }

        /**
         * Kiểm tra xem người dùng có quyền quản lý nhóm không (trưởng nhóm hoặc phó
         * nhóm)
         */
        private boolean hasManagePermission(GroupMember member) {
                return member.getRole() == GroupRole.LEADER || member.getRole() == GroupRole.DEPUTY;
        }

        @Transactional
        public void makeDeputy(Long groupId, Long userId, Long leaderId) {
                Group group = groupRepository.findById(groupId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhóm với id: " + groupId));

                // Kiểm tra xem người thực hiện có phải là trưởng nhóm không
                GroupMember leaderMember = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(leaderId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Không tìm thấy trưởng nhóm với id: " + leaderId)))
                                .orElseThrow(() -> new UnauthorizedException(
                                                "Trưởng nhóm không phải là thành viên của nhóm này"));

                if (leaderMember.getRole() != GroupRole.LEADER) {
                        throw new UnauthorizedException("Chỉ trưởng nhóm mới có thể bổ nhiệm phó nhóm");
                }

                // Tìm thành viên cần thăng chức
                GroupMember memberToPromote = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(userId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Không tìm thấy người dùng với id: " + userId)))
                                .orElseThrow(() -> new BadRequestException(
                                                "Người dùng không phải là thành viên của nhóm này"));

                if (memberToPromote.getRole() == GroupRole.LEADER) {
                        throw new BadRequestException("Không thể thay đổi vai trò của trưởng nhóm");
                }

                memberToPromote.setRole(GroupRole.DEPUTY);
                groupMemberRepository.save(memberToPromote);

                // Gửi thông báo thăng cấp thành viên
                if (group.getConversation() != null) {
                        conversationService.sendGroupMemberPromotedNotification(
                                        group.getConversation().getId(),
                                        memberToPromote.getUser().getDisplayName(),
                                        leaderMember.getUser().getDisplayName());
                }
        }

        @Transactional
        public void demoteToMember(Long groupId, Long userId, Long leaderId) {
                Group group = groupRepository.findById(groupId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhóm với id: " + groupId));

                // Kiểm tra xem người thực hiện có phải là trưởng nhóm không
                GroupMember leaderMember = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(leaderId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Không tìm thấy trưởng nhóm với id: " + leaderId)))
                                .orElseThrow(() -> new UnauthorizedException(
                                                "Trưởng nhóm không phải là thành viên của nhóm này"));

                if (leaderMember.getRole() != GroupRole.LEADER) {
                        throw new UnauthorizedException("Chỉ trưởng nhóm mới có thể hạ chức thành viên");
                }

                // Tìm thành viên cần hạ chức
                GroupMember memberToDemote = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(userId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Không tìm thấy người dùng với id: " + userId)))
                                .orElseThrow(() -> new BadRequestException(
                                                "Người dùng không phải là thành viên của nhóm này"));

                if (memberToDemote.getRole() == GroupRole.LEADER) {
                        throw new BadRequestException("Không thể hạ chức trưởng nhóm");
                }

                memberToDemote.setRole(GroupRole.MEMBER);
                groupMemberRepository.save(memberToDemote);

                // Gửi thông báo hạ cấp thành viên
                if (group.getConversation() != null) {
                        conversationService.sendGroupMemberDemotedNotification(
                                        group.getConversation().getId(),
                                        memberToDemote.getUser().getDisplayName(),
                                        leaderMember.getUser().getDisplayName());
                }
        }

        @Transactional
        public void transferLeadership(Long groupId, Long newLeaderId, Long currentLeaderId) {
                Group group = groupRepository.findById(groupId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhóm với id: " + groupId));

                // Kiểm tra xem người thực hiện có phải là trưởng nhóm không
                GroupMember currentLeader = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(currentLeaderId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Không tìm thấy trưởng nhóm hiện tại với id: "
                                                                                + currentLeaderId)))
                                .orElseThrow(() -> new UnauthorizedException(
                                                "Trưởng nhóm hiện tại không phải là thành viên của nhóm này"));

                if (currentLeader.getRole() != GroupRole.LEADER) {
                        throw new UnauthorizedException("Chỉ trưởng nhóm mới có thể chuyển giao quyền trưởng nhóm");
                }

                // Tìm thành viên được chọn làm trưởng nhóm mới
                GroupMember newLeader = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(newLeaderId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Không tìm thấy trưởng nhóm mới với id: "
                                                                                + newLeaderId)))
                                .orElseThrow(() -> new BadRequestException(
                                                "Trưởng nhóm mới không phải là thành viên của nhóm này"));

                // Lưu tên trước khi thay đổi vai trò
                String oldLeaderName = currentLeader.getUser().getDisplayName();
                String newLeaderName = newLeader.getUser().getDisplayName();

                // Chuyển quyền trưởng nhóm
                currentLeader.setRole(GroupRole.MEMBER);
                newLeader.setRole(GroupRole.LEADER);

                groupMemberRepository.save(currentLeader);
                groupMemberRepository.save(newLeader);

                // Gửi thông báo chuyển quyền trưởng nhóm
                if (group.getConversation() != null) {
                        conversationService.sendGroupLeadershipTransferredNotification(
                                        group.getConversation().getId(),
                                        newLeaderName,
                                        oldLeaderName);
                }
        }

        /**
         * Giải tán nhóm chat (chỉ trưởng nhóm mới có quyền giải tán)
         * 
         * @param groupId ID của nhóm cần giải tán
         * @param userId  ID của người dùng thực hiện giải tán (phải là trưởng nhóm)
         * @throws ResourceNotFoundException Nếu không tìm thấy nhóm hoặc người dùng
         * @throws UnauthorizedException     Nếu người dùng không phải là trưởng nhóm
         */
        @Transactional
        public void dissolveGroup(Long groupId, Long userId) {
                Group group = groupRepository.findById(groupId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhóm với id: " + groupId));

                // Kiểm tra xem người dùng có phải là trưởng nhóm không
                GroupMember member = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(userId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Không tìm thấy người dùng với id: " + userId)))
                                .orElseThrow(() -> new UnauthorizedException(
                                                "Bạn không phải là thành viên của nhóm này"));

                if (member.getRole() != GroupRole.LEADER) {
                        throw new UnauthorizedException("Chỉ trưởng nhóm mới có thể giải tán nhóm");
                }

                // Lấy thông tin về cuộc trò chuyện của nhóm (nếu có)
                Conversation conversation = group.getConversation();
                Long conversationId = null;
                if (conversation != null) {
                        conversationId = conversation.getId();
                }

                // Lấy danh sách thành viên trước khi xóa để gửi thông báo
                List<GroupMember> groupMembers = groupMemberRepository.findByGroup(group);
                List<Long> memberIds = groupMembers.stream()
                                .map(gm -> gm.getUser().getUserId())
                                .collect(Collectors.toList());

                // Gửi thông báo realtime đến tất cả thành viên về việc giải tán nhóm
                sendGroupDissolvedNotificationToMembers(group, memberIds, member.getUser().getDisplayName());

                // Xóa tất cả các thành viên của nhóm
                groupMemberRepository.deleteAll(groupMembers);

                // Xóa nhóm
                groupRepository.delete(group);

                // Xóa cuộc trò chuyện liên quan đến nhóm (nếu có)
                if (conversationId != null) {
                        conversationService.deleteConversation(conversationId);
                }
        }

        /**
         * Cho phép thành viên rời khỏi nhóm
         * 
         * @param groupId ID của nhóm
         * @param userId  ID của người dùng muốn rời khỏi nhóm
         * @throws ResourceNotFoundException Nếu không tìm thấy nhóm hoặc người dùng
         * @throws BadRequestException       Nếu người dùng không phải là thành viên
         *                                   hoặc là trưởng nhóm
         */
        @Transactional
        public void leaveGroup(Long groupId, Long userId) {
                Group group = groupRepository.findById(groupId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy nhóm với id: " + groupId));

                // Tìm thành viên muốn rời khỏi
                GroupMember member = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(userId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Không tìm thấy người dùng với id: " + userId)))
                                .orElseThrow(() -> new BadRequestException(
                                                "Bạn không phải là thành viên của nhóm này"));

                // Trưởng nhóm không được phép rời khỏi nhóm (phải chuyển quyền trước)
                if (member.getRole() == GroupRole.LEADER) {
                        throw new BadRequestException(
                                        "Trưởng nhóm không thể rời khỏi nhóm. Vui lòng chuyển quyền trưởng nhóm trước khi rời khỏi");
                }

                // Lưu tên thành viên trước khi xóa
                String memberName = member.getUser().getDisplayName();

                // Xóa thành viên khỏi nhóm
                groupMemberRepository.delete(member);

                // Xóa người dùng khỏi cuộc trò chuyện của nhóm (nếu có)
                if (group.getConversation() != null) {
                        conversationService.removeUserFromConversation(group.getConversation().getId(), userId);

                        // Gửi thông báo thành viên rời khỏi nhóm
                        conversationService.sendGroupMemberLeftNotification(
                                        group.getConversation().getId(),
                                        memberName);
                }

                // Gửi thông báo realtime đến thành viên rời khỏi nhóm
                sendMemberLeftNotificationToUser(userId, group, memberName);

                // Gửi thông báo realtime đến các thành viên còn lại trong nhóm
                sendMemberLeftNotificationToRemainingMembers(group, memberName);
        }

        /**
         * Chuyển đổi đối tượng Group thành GroupDto
         * 
         * @param group Đối tượng Group cần chuyển đổi
         * @return GroupDto Đối tượng DTO tương ứng
         */
        private GroupDto mapToDto(Group group) {
                GroupDto dto = new GroupDto();
                dto.setGroupId(group.getGroupId());
                dto.setName(group.getName());
                dto.setAvatarUrl(group.getAvatarUrl());
                dto.setCreatedAt(group.getCreatedAt());

                List<GroupMemberDto> memberDtos = groupMemberRepository.findByGroup(group).stream()
                                .map(this::mapMemberToDto)
                                .collect(Collectors.toList());

                dto.setMembers(memberDtos);

                // Thêm conversationId từ mối quan hệ với conversation
                if (group.getConversation() != null) {
                        dto.setConversationId(group.getConversation().getId());
                }

                return dto;
        }

        /**
         * Chuyển đổi đối tượng GroupMember thành GroupMemberDto
         * 
         * @param member Đối tượng GroupMember cần chuyển đổi
         * @return GroupMemberDto Đối tượng DTO tương ứng
         */
        private GroupMemberDto mapMemberToDto(GroupMember member) {
                GroupMemberDto dto = new GroupMemberDto();
                dto.setId(member.getId());
                dto.setGroupId(member.getGroup().getGroupId());

                UserDto userDto = new UserDto();
                userDto.setUserId(member.getUser().getUserId());
                userDto.setDisplayName(member.getUser().getDisplayName());
                userDto.setPhone(member.getUser().getPhone());
                userDto.setAvatarUrl(member.getUser().getAvatarUrl());

                dto.setUser(userDto);
                dto.setRole(member.getRole());
                return dto;
        }

        /**
         * Gửi thông báo realtime đến tất cả thành viên về việc tạo nhóm mới
         * 
         * @param group       Nhóm vừa được tạo
         * @param memberIds   Danh sách ID các thành viên trong nhóm
         * @param creatorName Tên người tạo nhóm
         */
        private void sendGroupCreatedNotificationToMembers(Group group, List<Long> memberIds, String creatorName) {
                // Tạo thông báo cho từng thành viên
                for (Long memberId : memberIds) {
                        // Gửi thông báo đến queue cá nhân của từng thành viên về việc được thêm vào
                        // nhóm mới
                        GroupDto groupNotification = mapToDto(group);
                        messagingTemplate.convertAndSend("/queue/user/" + memberId + "/group-created",
                                        Map.of(
                                                        "type", "GROUP_CREATED",
                                                        "message", String.format("Bạn đã được %s thêm vào nhóm \"%s\"",
                                                                        creatorName, group.getName()),
                                                        "group", groupNotification,
                                                        "creatorName", creatorName,
                                                        "timestamp", LocalDateTime.now()));
                }
        }

        /**
         * Gửi thông báo đến thành viên bị xóa khỏi nhóm
         * 
         * @param userId      ID của thành viên bị xóa
         * @param group       Nhóm mà thành viên bị xóa khỏi
         * @param memberName  Tên của thành viên bị xóa
         * @param managerName Tên của người quản lý thực hiện xóa
         */
        private void sendMemberRemovedNotificationToUser(Long userId, Group group, String memberName,
                        String managerName) {
                messagingTemplate.convertAndSend("/queue/user/" + userId + "/group-member-removed",
                                Map.of(
                                                "type", "GROUP_MEMBER_REMOVED",
                                                "message", String.format("Bạn đã bị %s xóa khỏi nhóm \"%s\"",
                                                                managerName, group.getName()),
                                                "groupName", group.getName(),
                                                "groupId", group.getGroupId(),
                                                "managerName", managerName,
                                                "timestamp", LocalDateTime.now()));
        }

        /**
         * Gửi thông báo đến các thành viên còn lại về việc có thành viên bị xóa khỏi
         * nhóm
         * 
         * @param group       Nhóm có thành viên bị xóa
         * @param memberName  Tên của thành viên bị xóa
         * @param managerName Tên của người quản lý thực hiện xóa
         */
        private void sendMemberRemovedNotificationToRemainingMembers(Group group, String memberName,
                        String managerName) {
                List<GroupMember> remainingMembers = groupMemberRepository.findByGroup(group);
                for (GroupMember member : remainingMembers) {
                        // Gửi thông báo đến tất cả thành viên còn lại
                        messagingTemplate.convertAndSend(
                                        "/queue/user/" + member.getUser().getUserId() + "/group-member-removed",
                                        Map.of(
                                                        "type", "GROUP_MEMBER_REMOVED",
                                                        "message",
                                                        String.format("Thành viên %s đã bị %s xóa khỏi nhóm \"%s\"",
                                                                        memberName, managerName, group.getName()),
                                                        "groupName", group.getName(),
                                                        "groupId", group.getGroupId(),
                                                        "memberName", memberName,
                                                        "managerName", managerName,
                                                        "timestamp", LocalDateTime.now()));
                }
        }

        /**
         * Gửi thông báo realtime đến thành viên được thêm vào nhóm
         * 
         * @param userId      ID của thành viên được thêm vào nhóm
         * @param group       Nhóm được thêm thành viên
         * @param memberName  Tên của thành viên được thêm vào nhóm
         * @param managerName Tên của người quản lý thêm thành viên
         */
        private void sendMemberAddedNotificationToNewMember(Long userId, Group group, String memberName,
                        String managerName) {
                messagingTemplate.convertAndSend("/queue/user/" + userId + "/group-member-added",
                                Map.of(
                                                "type", "GROUP_MEMBER_ADDED",
                                                "message", String.format("Bạn đã được %s thêm vào nhóm \"%s\"",
                                                                managerName, group.getName()),
                                                "groupName", group.getName(),
                                                "groupId", group.getGroupId(),
                                                "memberName", memberName,
                                                "managerName", managerName,
                                                "timestamp", LocalDateTime.now()));
        }

        /**
         * Gửi thông báo realtime đến các thành viên hiện tại về việc có thành viên mới
         * 
         * @param group       Nhóm được thêm thành viên
         * @param memberName  Tên của thành viên được thêm vào nhóm
         * @param managerName Tên của người quản lý thêm thành viên
         */
        private void sendMemberAddedNotificationToExistingMembers(Group group, String memberName,
                        String managerName) {
                List<GroupMember> existingMembers = groupMemberRepository.findByGroup(group);
                for (GroupMember member : existingMembers) {
                        // Gửi thông báo đến tất cả thành viên còn lại
                        messagingTemplate.convertAndSend(
                                        "/queue/user/" + member.getUser().getUserId() + "/group-member-added",
                                        Map.of(
                                                        "type", "GROUP_MEMBER_ADDED",
                                                        "message",
                                                        String.format("Thành viên %s đã được %s thêm vào nhóm \"%s\"",
                                                                        memberName, managerName, group.getName()),
                                                        "groupName", group.getName(),
                                                        "groupId", group.getGroupId(),
                                                        "memberName", memberName,
                                                        "managerName", managerName,
                                                        "timestamp", LocalDateTime.now()));
                }
        }

        /**
         * Gửi thông báo realtime đến tất cả thành viên về việc giải tán nhóm
         * 
         * @param group      Nhóm vừa được giải tán
         * @param memberIds  Danh sách ID các thành viên trong nhóm
         * @param leaderName Tên của trưởng nhóm thực hiện giải tán
         */
        private void sendGroupDissolvedNotificationToMembers(Group group, List<Long> memberIds, String leaderName) {
                // Tạo thông báo cho từng thành viên
                for (Long memberId : memberIds) {
                        // Gửi thông báo đến queue cá nhân của từng thành viên về việc nhóm đã được giải
                        // tán
                        GroupDto groupNotification = mapToDto(group);
                        messagingTemplate.convertAndSend("/queue/user/" + memberId + "/group-dissolved",
                                        Map.of(
                                                        "type", "GROUP_DISSOLVED",
                                                        "message", String.format("Nhóm \"%s\" đã được %s giải tán",
                                                                        group.getName(), leaderName),
                                                        "group", groupNotification,
                                                        "leaderName", leaderName,
                                                        "timestamp", LocalDateTime.now()));
                }
        }

        /**
         * Gửi thông báo realtime đến thành viên rời khỏi nhóm
         * 
         * @param userId     ID của thành viên rời khỏi nhóm
         * @param group      Nhóm mà thành viên rời khỏi
         * @param memberName Tên của thành viên rời khỏi nhóm
         */
        private void sendMemberLeftNotificationToUser(Long userId, Group group, String memberName) {
                messagingTemplate.convertAndSend("/queue/user/" + userId + "/group-member-left",
                                Map.of(
                                                "type", "GROUP_MEMBER_LEFT",
                                                "message", String.format("Bạn đã rời khỏi nhóm \"%s\"",
                                                                group.getName()),
                                                "groupName", group.getName(),
                                                "groupId", group.getGroupId(),
                                                "memberName", memberName,
                                                "timestamp", LocalDateTime.now()));
        }

        /**
         * Gửi thông báo realtime đến các thành viên còn lại trong nhóm
         * 
         * @param group      Nhóm mà thành viên rời khỏi
         * @param memberName Tên của thành viên rời khỏi nhóm
         */
        private void sendMemberLeftNotificationToRemainingMembers(Group group, String memberName) {
                List<GroupMember> remainingMembers = groupMemberRepository.findByGroup(group);
                for (GroupMember member : remainingMembers) {
                        // Gửi thông báo đến tất cả thành viên còn lại
                        messagingTemplate.convertAndSend(
                                        "/queue/user/" + member.getUser().getUserId() + "/group-member-left",
                                        Map.of(
                                                        "type", "GROUP_MEMBER_LEFT",
                                                        "message",
                                                        String.format("Thành viên %s đã rời khỏi nhóm \"%s\"",
                                                                        memberName, group.getName()),
                                                        "groupName", group.getName(),
                                                        "groupId", group.getGroupId(),
                                                        "memberName", memberName,
                                                        "timestamp", LocalDateTime.now()));
                }
        }
}