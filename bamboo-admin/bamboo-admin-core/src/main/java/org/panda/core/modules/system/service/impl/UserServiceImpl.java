package org.panda.core.modules.system.service.impl;

import com.github.pagehelper.Page;
import org.apache.commons.collections.CollectionUtils;
import org.apache.shiro.SecurityUtils;
import org.panda.common.domain.ResultConstant;
import org.panda.core.common.constant.enumeration.RoleType;
import org.panda.core.common.exception.SystemException;
import org.panda.core.modules.system.dao.RoleDao;
import org.panda.core.modules.system.dao.UserDao;
import org.panda.core.modules.system.domain.dto.UserDTO;
import org.panda.core.modules.system.domain.po.RolePO;
import org.panda.core.modules.system.domain.po.UserPO;
import org.panda.core.modules.system.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserServiceImpl implements UserService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private UserDao userDao;

    @Autowired
    private RoleDao roleDao;

    @Override
    public Page<UserPO> getUsers(String keyword) {
        return userDao.findUsers(keyword);
    }

    @Override
    public UserPO getUserInfo(String username) {
        return userDao.findByUsername(username);
    }

    @Override
    public UserDTO getUserAndRoles(String username) {
        UserPO userPO = userDao.findByUsername(username);
        UserDTO userDto = new UserDTO(userPO);
        userDto.setRoles(roleDao.findByUserId(userPO.getId()));
        return userDto;
    }

    @Override
    public String addUser(UserPO user) {
        // 校验username不能重复
        String username = user.getUsername();
        UserPO userPO = userDao.findByUsername(username);
        if (userPO != null) {
            String msg = "The username is already taken!";
            LOGGER.error(msg);
            return msg;
        }
        userDao.insertUser(user);
        return ResultConstant.DEFAULT_SUCESS_MSG;
    }

    @Override
    public int updateUser(UserPO user) {
        // 更新时间
        user.setUpdateTime(new Date());
        return userDao.updateUser(user);
    }

    @Override
    public int deleteUser(String username) throws SystemException{
        if (!checkTopRoles()) {
            return -1;
        }

        // 删除操作排除自己
        UserPO user = (UserPO) SecurityUtils.getSubject().getPrincipal();
        String principalUsername = user.getUsername();
        if (username.equals(principalUsername)) {
            throw new SystemException("Can't delete yourself.");
        }

        // 校验是否绑定的有角色
        UserDTO useDto = this.getUserAndRoles(username);
        List<RolePO> roles = useDto.getRoles();
        if (CollectionUtils.isNotEmpty(roles)) {
            throw new SystemException("Please unbind this user's role first.");
        }

        return userDao.deleteUser(username);
    }

    @Override
    public boolean checkRoleUpdatedPass() {
        return checkTopRoles();
    }

    private boolean checkTopRoles() {
        UserPO user = (UserPO) SecurityUtils.getSubject().getPrincipal();
        String principalUsername = user.getUsername();

        UserDTO useDto = this.getUserAndRoles(principalUsername);
        List<RolePO> roles = useDto.getRoles();
        if (CollectionUtils.isEmpty(roles)) {
            return false;
        }

        List<String> topRoles = RoleType.getTopRoles();
        List<RolePO> result = roles.stream()
                .filter(role -> topRoles.contains(role.getRoleCode()))
                .collect(Collectors.toList());
        return CollectionUtils.isNotEmpty(result);
    }
}
