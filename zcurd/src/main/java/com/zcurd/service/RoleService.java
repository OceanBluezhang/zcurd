package com.zcurd.service;

import com.jfinal.aop.Before;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.tx.Tx;
import com.zcurd.common.StringUtil;

/**
 * 角色权限管理（权限实现请查看LoginService）
 * @author 钟世云 2016.2.17
 */
public class RoleService {
	
	/**
	 * 保存权限
	 */
	@Before(Tx.class)
	public void saveAuth(String menuIds, String btnIds, int roleId) {
		//保存菜单权限
		Db.update("delete from sys_role_menu where role_id=?", roleId);
		if(StringUtil.isNotEmpty(menuIds)) {
			for (String menuId : menuIds.split(",")) {
				Db.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)", new Object[]{roleId, menuId});
			}
		}
		//保存按钮权限
		Db.update("delete from sys_role_btn where role_id=?", roleId);
		if(StringUtil.isNotEmpty(btnIds)) {
			for (String btnId : btnIds.split(",")) {
				Db.update("INSERT INTO sys_role_btn (role_id, btn_id) VALUES (?, ?)", new Object[]{roleId, btnId});
			}
		}
	}

}