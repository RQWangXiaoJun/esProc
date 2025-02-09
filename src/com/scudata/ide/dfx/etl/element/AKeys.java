package com.scudata.ide.dfx.etl.element;

import java.util.ArrayList;

import com.scudata.ide.dfx.etl.EtlConsts;
import com.scudata.ide.dfx.etl.ObjectElement;
import com.scudata.ide.dfx.etl.ParamInfo;
import com.scudata.ide.dfx.etl.ParamInfoList;

/**
 * 辅助函数编辑 A.keys()
 * 函数名前缀A表示序表
 * 
 * @author Joancy
 *
 */
public class AKeys extends ObjectElement {
	public ArrayList<String> keys;
	
	/**
	 * 获取用于界面编辑的参数信息列表
	 */
	public ParamInfoList getParamInfoList() {
		ParamInfoList paramInfos = new ParamInfoList();
		ParamInfo.setCurrent(AKeys.class, this);

		paramInfos.add(new ParamInfo("keys",EtlConsts.INPUT_STRINGLIST));

		return paramInfos;
	}


	/**
	 * 获取父类型
	 * 类型的常量定义为
	 * EtlConsts.TYPE_XXX
	 * @return 前缀A开头的函数，均返回EtlConsts.TYPE_SEQUENCE
	 */
	public byte getParentType() {
		return EtlConsts.TYPE_SEQUENCE;
	}

	/**
	 * 获取该函数的返回类型
	 * @return EtlConsts.TYPE_SEQUENCE
	 */
	public byte getReturnType() {
		return EtlConsts.TYPE_SEQUENCE;
	}

	/**
	 * 获取用于生成SPL表达式的选项串
	 */
	public String optionString(){
		return "";
	}

	/**
	 * 获取用于生成SPL表达式的函数名
	 */
	public String getFuncName() {
		return "keys";
	}

	/**
	 * 获取用于生成SPL表达式的函数体
	 * 跟setFuncBody是逆函数，然后表达式的赋值也总是互逆的
	 */
	public String getFuncBody() {
		return getStringListExp(keys,",");
	}

	/**
	 * 设置函数体
	 * @param funcBody 函数体
	 */
	public boolean setFuncBody(String funcBody) {
		keys = getStringList(funcBody,",");
		return true;
	}

}
