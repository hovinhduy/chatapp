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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
         */
        public GroupService(GroupRepository groupRepository, GroupMemberRepository groupMemberRepository,
                        UserRepository userRepository, FriendRepository friendRepository,
                        ConversationService conversationService) {
                this.groupRepository = groupRepository;
                this.groupMemberRepository = groupMemberRepository;
                this.userRepository = userRepository;
                this.friendRepository = friendRepository;
                this.conversationService = conversationService;
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
                                                "Group not found with id: " + groupId));
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
                                                "Group not found with id: " + groupId));

                // Check if user is admin
                GroupMember member = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(userId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "User not found with id: " + userId)))
                                .orElseThrow(() -> new UnauthorizedException("You are not a member of this group"));

                if (!member.getRole().equals(GroupRole.LEADER)) {
                        throw new UnauthorizedException("Only group admin can update group details");
                }

                group.setName(groupDto.getName());
                if (groupDto.getAvatarUrl() != null) {
                        group.setAvatarUrl(groupDto.getAvatarUrl());
                }

                Group updatedGroup = groupRepository.save(group);
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
                                                "Group not found with id: " + groupId));

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

                // Kiểm tra quyền quản lý
                GroupMember managerMember = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(managerId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Manager not found with id: " + managerId)))
                                .orElseThrow(() -> new UnauthorizedException("Manager is not a member of this group"));

                if (!hasManagePermission(managerMember)) {
                        throw new UnauthorizedException("Only group leader or deputy can add members");
                }

                // Check if user is already a member
                if (groupMemberRepository.existsByGroupAndUser(group, user)) {
                        throw new BadRequestException("User is already a member of this group");
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
                }
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
                                                "Group not found with id: " + groupId));

                // Kiểm tra quyền quản lý
                GroupMember managerMember = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(managerId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Manager not found with id: " + managerId)))
                                .orElseThrow(() -> new UnauthorizedException("Manager is not a member of this group"));

                if (!hasManagePermission(managerMember)) {
                        throw new UnauthorizedException("Only group leader or deputy can remove members");
                }

                // Tìm thành viên cần xóa
                GroupMember memberToRemove = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(userId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "User not found with id: " + userId)))
                                .orElseThrow(() -> new BadRequestException("User is not a member of this group"));

                // Không thể xóa trưởng nhóm
                if (memberToRemove.getRole() == GroupRole.LEADER) {
                        throw new UnauthorizedException("Cannot remove group leader");
                }

                // Phó nhóm chỉ có thể xóa thành viên thường
                if (managerMember.getRole() == GroupRole.DEPUTY && memberToRemove.getRole() == GroupRole.DEPUTY) {
                        throw new UnauthorizedException("Deputy cannot remove another deputy");
                }

                groupMemberRepository.delete(memberToRemove);

                // Xóa người dùng khỏi cuộc trò chuyện của nhóm (nếu có)
                if (group.getConversation() != null) {
                        conversationService.removeUserFromConversation(group.getConversation().getId(), userId);
                }
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
                                                "Group not found with id: " + groupId));

                // Kiểm tra xem người thực hiện có phải là trưởng nhóm không
                GroupMember leaderMember = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(leaderId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Leader not found with id: " + leaderId)))
                                .orElseThrow(() -> new UnauthorizedException("Leader is not a member of this group"));

                if (leaderMember.getRole() != GroupRole.LEADER) {
                        throw new UnauthorizedException("Only group leader can assign deputy roles");
                }

                // Tìm thành viên cần thăng chức
                GroupMember memberToPromote = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(userId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "User not found with id: " + userId)))
                                .orElseThrow(() -> new BadRequestException("User is not a member of this group"));

                if (memberToPromote.getRole() == GroupRole.LEADER) {
                        throw new BadRequestException("Cannot change role of group leader");
                }

                memberToPromote.setRole(GroupRole.DEPUTY);
                groupMemberRepository.save(memberToPromote);
        }

        @Transactional
        public void demoteToMember(Long groupId, Long userId, Long leaderId) {
                Group group = groupRepository.findById(groupId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Group not found with id: " + groupId));

                // Kiểm tra xem người thực hiện có phải là trưởng nhóm không
                GroupMember leaderMember = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(leaderId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Leader not found with id: " + leaderId)))
                                .orElseThrow(() -> new UnauthorizedException("Leader is not a member of this group"));

                if (leaderMember.getRole() != GroupRole.LEADER) {
                        throw new UnauthorizedException("Only group leader can demote members");
                }

                // Tìm thành viên cần hạ chức
                GroupMember memberToDemote = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(userId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "User not found with id: " + userId)))
                                .orElseThrow(() -> new BadRequestException("User is not a member of this group"));

                if (memberToDemote.getRole() == GroupRole.LEADER) {
                        throw new BadRequestException("Cannot demote group leader");
                }

                memberToDemote.setRole(GroupRole.MEMBER);
                groupMemberRepository.save(memberToDemote);
        }

        @Transactional
        public void transferLeadership(Long groupId, Long newLeaderId, Long currentLeaderId) {
                Group group = groupRepository.findById(groupId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Group not found with id: " + groupId));

                // Kiểm tra xem người thực hiện có phải là trưởng nhóm không
                GroupMember currentLeader = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(currentLeaderId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Current leader not found with id: "
                                                                                + currentLeaderId)))
                                .orElseThrow(() -> new UnauthorizedException(
                                                "Current leader is not a member of this group"));

                if (currentLeader.getRole() != GroupRole.LEADER) {
                        throw new UnauthorizedException("Only group leader can transfer leadership");
                }

                // Tìm thành viên được chọn làm trưởng nhóm mới
                GroupMember newLeader = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(newLeaderId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "New leader not found with id: " + newLeaderId)))
                                .orElseThrow(() -> new BadRequestException("New leader is not a member of this group"));

                // Chuyển quyền trưởng nhóm
                currentLeader.setRole(GroupRole.MEMBER);
                newLeader.setRole(GroupRole.LEADER);

                groupMemberRepository.save(currentLeader);
                groupMemberRepository.save(newLeader);
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
}