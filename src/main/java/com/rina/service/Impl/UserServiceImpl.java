package com.rina.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rina.domain.RoleUser;
import com.rina.domain.User;
import com.rina.domain.dto.RoleUserDto;
import com.rina.domain.dto.UserDto;
import com.rina.domain.vo.UserDetailsVo;
import com.rina.enums.ResultCode;
import com.rina.mapper.*;
import com.rina.resp.Resp;
import com.rina.resp.UsualResp;
import com.rina.service.UserService;
import com.rina.util.JwtUtil;
import com.rina.util.MyThreadLocal;
import com.rina.util.RespUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户管理和登陆相关的service
 *
 * @author arvin
 * @date 2022/02/28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

	private final UserMapper userMapper;
	private final RoleUserMapper roleUserMapper;
	private final MenuMapper menuMapper;
	private final RoleMapper roleMapper;
	private final RoleMenuMapper roleMenuMapper;
	private final SongListMapper songListMapper;
	private final JwtUtil jwtUtil;

	@Override
	public Resp login(String username, String password) {
		String token = null;
		try {
			final User user = userMapper.login(username);

			if (!user.getStatus()) {
				return Resp.failed();
			}

			if (BCrypt.checkpw(password, user.getPassword())) {
				final UserDetailsVo userDetailsVo = new UserDetailsVo(
						user.getUserName(),
						roleUserMapper.findRoleByUser(user.getId()).getRoleId()
				);
				token = jwtUtil.createJwtToken(userDetailsVo);
			} else {
				return Resp.failed();
			}
		} catch (JsonProcessingException e) {
			log.error("Json解析错误：{}", e.getLocalizedMessage());
			return Resp.failed(ResultCode.INTERNAL_SERVER_ERROR);
		}

		return UsualResp.succeed(token);
	}

	@Override
	public Resp updateToken(String newUserName) {
		final Long currentRole = Long.valueOf(MyThreadLocal.get().get("roleId"));

		String token = null;
		try {
			token = jwtUtil.createJwtToken(new UserDetailsVo(newUserName, currentRole));
		} catch (JsonProcessingException e) {
			log.error("Json解析错误：{}", e.getLocalizedMessage());
			return Resp.failed(ResultCode.INTERNAL_SERVER_ERROR);
		}

		return UsualResp.succeed(token);
	}

	@Override
	public Resp listUsers() {
		final List<UserDto> userDtos = userMapper.getAllUsers()
				.stream()
				.map(user -> {
					final UserDto dto = new UserDto();
					BeanUtils.copyProperties(user, dto);
					dto.setPassword(null);
					return dto;
				})
				.peek(userDto -> {
					userDto.setRoleId(roleUserMapper.findRoleByUser(userDto.getId())
							.getRole().getId());
					userDto.setRoleName(roleUserMapper.findRoleByUser(userDto.getId())
							.getRole().getRole());
				})
				.collect(Collectors.toList());

		return RespUtils.queryData(userDtos);
	}

	@Override
	public Resp getSingleUser(Long userId) {
		final User user = userMapper.getOneUser(userId);

		if (user == null) {
			log.error("查询数据不存在");
			return Resp.failed();
		}

		final UserDto userDto = UserDto.builder()
				.id(userId)
				.userName(user.getUserName())
				.status(user.getStatus())
				.roleName(roleUserMapper.findRoleByUser(userId)
						.getRole().getRole())
				.createBy(user.getCreateBy())
				.updateBy(user.getUpdateBy())
				.build();

		return UsualResp.succeed(userDto);
	}

	@Override
	public Resp editUser(UserDto userDto) {
		final String currentUser = MyThreadLocal.get().get("userName");
		log.info("当前用户为：{}", currentUser);

		int userResult = 0;
		int roleResult = 0;
		if (userDto.getId() == null || userDto.getId() == 0) {
			// 添加一个用户
			final User user = User.builder()
					.userName(userDto.getUserName())
					.password(BCrypt.hashpw(userDto.getPassword(), BCrypt.gensalt()))
					.status(userDto.getStatus())
					.createBy(currentUser)
					.createTime(new Date())
					.updateBy(currentUser)
					.updateTime(new Date())
					.build();
			userResult = userMapper.insert(user);

			final Long userId = userMapper.getNewestUserId();
			final RoleUser roleUser = RoleUser.builder()
					.roleId(2L)
					.userId(userId)
					.createBy(currentUser)
					.createTime(new Date())
					.updateBy(currentUser)
					.updateTime(new Date())
					.build();
			roleResult = roleUserMapper.insert(roleUser);
		} else {
			// 更改一个用户
			User user = userMapper.getOneUser(userDto.getId());

			// 更新前做数据可用性检查
			if (dataUsableCheck(userDto.getUserName()) && !user.getUserName().equals(userDto.getUserName())) {
				userMapper.updateEditorName(user.getUserName(), userDto.getUserName());
				roleUserMapper.updateEditorName(user.getUserName(), userDto.getUserName());
				menuMapper.updateEditorName(user.getUserName(), userDto.getUserName());
				roleMapper.updateEditorName(user.getUserName(), userDto.getUserName());
				roleMenuMapper.updateEditorName(user.getUserName(), userDto.getUserName());
				songListMapper.updateEditorName(user.getUserName(), userDto.getUserName());

				user = user.withUserName(userDto.getUserName());
			}
			if (dataUsableCheck(userDto.getPassword())) {
				user = user.withPassword(BCrypt.hashpw(userDto.getPassword(), BCrypt.gensalt()));
			}
			user = user.withStatus(userDto.getStatus());
			user = user.withUpdateBy(currentUser);
			user = user.withUpdateTime(new Date());

			userResult = userMapper.updateOneUserByUserId(user);
			roleResult = 1;
		}

		return RespUtils.editData(userResult, roleResult);
	}

	@Override
	public Resp changeRole(RoleUserDto roleUserDto) {
		final String currentUser = MyThreadLocal.get().get("userName");

		RoleUser roleUser = roleUserMapper.findRoleByUser(roleUserDto.getUserId());

		roleUser = roleUser.withRoleId(roleUserDto.getRoleId());
		roleUser = roleUser.withUpdateBy(currentUser);
		roleUser = roleUser.withUpdateTime(new Date());

		final int changeResult = roleUserMapper.changeRoleByUser(roleUser);
		return RespUtils.editData(changeResult);
	}

	@Override
	public Resp deleteUser(Long userId) {
		final int userResult = userMapper.deleteOneUser(userId);
		final int roleResult = roleUserMapper.deleteByUserId(userId);

		return RespUtils.deleteData(userResult, roleResult);
	}
}
