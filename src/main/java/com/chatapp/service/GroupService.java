package com.chatapp.service;

import com.chatapp.dto.GroupDto;
import com.chatapp.dto.GroupMemberDto;
import com.chatapp.dto.UserDto;
import com.chatapp.exception.BadRequestException;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.exception.UnauthorizedException;
import com.chatapp.model.Group;
import com.chatapp.model.GroupMember;
import com.chatapp.model.User;
import com.chatapp.repository.GroupMemberRepository;
import com.chatapp.repository.GroupRepository;
import com.chatapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        /**
         * Constructor để dependency injection
         * 
         * @param groupRepository       Repository xử lý thao tác với database của Group
         * @param groupMemberRepository Repository xử lý thao tác với database của
         *                              GroupMember
         * @param userRepository        Repository xử lý thao tác với database của User
         */
        public GroupService(GroupRepository groupRepository, GroupMemberRepository groupMemberRepository,
                        UserRepository userRepository) {
                this.groupRepository = groupRepository;
                this.groupMemberRepository = groupMemberRepository;
                this.userRepository = userRepository;
        }

        /**
         * Tạo mới nhóm chat
         * 
         * @param groupDto  Đối tượng chứa thông tin nhóm cần tạo
         * @param creatorId ID của người tạo nhóm
         * @return GroupDto Thông tin nhóm đã được tạo
         * @throws ResourceNotFoundException Nếu không tìm thấy người tạo nhóm
         */
        @Transactional
        public GroupDto createGroup(GroupDto groupDto, Long creatorId) {
                // Kiểm tra xem có ít nhất 2 thành viên khác được thêm vào nhóm
                if (groupDto.getMembers() == null || groupDto.getMembers().size() < 2) {
                        throw new BadRequestException("Group must have at least 2 members besides the creator");
                }

                User creator = userRepository.findById(creatorId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "User not found with id: " + creatorId));

                Group group = new Group();
                group.setName(groupDto.getName());
                group.setAvatarUrl(groupDto.getAvatarUrl());

                Group savedGroup = groupRepository.save(group);

                // Add creator as admin
                GroupMember groupMember = new GroupMember();
                groupMember.setGroup(savedGroup);
                groupMember.setUser(creator);
                groupMember.setRole(true); // Admin role
                groupMemberRepository.save(groupMember);

                // Thêm các thành viên khác từ danh sách
                for (GroupMemberDto memberDto : groupDto.getMembers()) {
                        User member = userRepository.findById(memberDto.getUser().getUserId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                        "User not found with id: " + memberDto.getUser().getUserId()));

                        // Kiểm tra không trùng với người tạo
                        if (member.getUserId().equals(creatorId)) {
                                continue;
                        }

                        GroupMember newMember = new GroupMember();
                        newMember.setGroup(savedGroup);
                        newMember.setUser(member);
                        newMember.setRole(false); // Regular member
                        groupMemberRepository.save(newMember);
                }

                return mapToDto(savedGroup);
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

                if (!member.isRole()) {
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
         * @param groupId ID của nhóm
         * @param userId  ID của người dùng cần thêm vào nhóm
         * @param adminId ID của admin thực hiện thêm thành viên
         * @throws ResourceNotFoundException Nếu không tìm thấy nhóm, người dùng hoặc
         *                                   admin
         * @throws UnauthorizedException     Nếu người thực hiện không phải là admin của
         *                                   nhóm
         * @throws BadRequestException       Nếu người dùng đã là thành viên của nhóm
         */
        @Transactional
        public void addMember(Long groupId, Long userId, Long adminId) {
                Group group = groupRepository.findById(groupId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Group not found with id: " + groupId));

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

                // Check if admin is admin
                GroupMember adminMember = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(adminId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Admin not found with id: " + adminId)))
                                .orElseThrow(() -> new UnauthorizedException("Admin is not a member of this group"));

                if (!adminMember.isRole()) {
                        throw new UnauthorizedException("Only group admin can add members");
                }

                // Check if user is already a member
                if (groupMemberRepository.existsByGroupAndUser(group, user)) {
                        throw new BadRequestException("User is already a member of this group");
                }

                GroupMember groupMember = new GroupMember();
                groupMember.setGroup(group);
                groupMember.setUser(user);
                groupMember.setRole(false); // Regular member
                groupMemberRepository.save(groupMember);
        }

        /**
         * Xóa thành viên khỏi nhóm
         * 
         * @param groupId ID của nhóm
         * @param userId  ID của người dùng cần xóa khỏi nhóm
         * @param adminId ID của admin thực hiện xóa thành viên
         * @throws ResourceNotFoundException Nếu không tìm thấy nhóm, người dùng hoặc
         *                                   admin
         * @throws UnauthorizedException     Nếu người thực hiện không phải là admin
         *                                   hoặc cố gắng xóa admin khác
         * @throws BadRequestException       Nếu người dùng không phải là thành viên của
         *                                   nhóm
         */
        @Transactional
        public void removeMember(Long groupId, Long userId, Long adminId) {
                Group group = groupRepository.findById(groupId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Group not found with id: " + groupId));

                // Check if admin is admin
                GroupMember adminMember = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(adminId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Admin not found with id: " + adminId)))
                                .orElseThrow(() -> new UnauthorizedException("Admin is not a member of this group"));

                if (!adminMember.isRole()) {
                        throw new UnauthorizedException("Only group admin can remove members");
                }

                // Find member to remove
                GroupMember memberToRemove = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(userId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "User not found with id: " + userId)))
                                .orElseThrow(() -> new BadRequestException("User is not a member of this group"));

                // Cannot remove another admin
                if (memberToRemove.isRole() && !adminId.equals(userId)) {
                        throw new UnauthorizedException("Cannot remove another admin");
                }

                groupMemberRepository.delete(memberToRemove);
        }

        /**
         * Nâng cấp một thành viên thành admin của nhóm
         * 
         * @param groupId ID của nhóm
         * @param userId  ID của người dùng cần nâng cấp thành admin
         * @param adminId ID của admin thực hiện nâng cấp
         * @throws ResourceNotFoundException Nếu không tìm thấy nhóm, người dùng hoặc
         *                                   admin
         * @throws UnauthorizedException     Nếu người thực hiện không phải là admin của
         *                                   nhóm
         * @throws BadRequestException       Nếu người dùng không phải là thành viên của
         *                                   nhóm
         */
        @Transactional
        public void makeAdmin(Long groupId, Long userId, Long adminId) {
                Group group = groupRepository.findById(groupId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Group not found with id: " + groupId));

                // Check if current user is admin
                GroupMember adminMember = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(adminId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "Admin not found with id: " + adminId)))
                                .orElseThrow(() -> new UnauthorizedException("Admin is not a member of this group"));

                if (!adminMember.isRole()) {
                        throw new UnauthorizedException("Only group admin can assign admin roles");
                }

                // Find member to promote
                GroupMember memberToPromote = groupMemberRepository.findByGroupAndUser(group,
                                userRepository.findById(userId)
                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                "User not found with id: " + userId)))
                                .orElseThrow(() -> new BadRequestException("User is not a member of this group"));

                memberToPromote.setRole(true);
                groupMemberRepository.save(memberToPromote);
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
                dto.setAdmin(member.isRole());
                return dto;
        }
}