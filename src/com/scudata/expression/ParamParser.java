package com.scudata.expression;

import java.util.ArrayList;
import java.util.List;

import com.scudata.cellset.ICellSet;
import com.scudata.cellset.INormalCell;
import com.scudata.common.ArgumentTokenizer;
import com.scudata.common.MessageManager;
import com.scudata.common.RQException;
import com.scudata.common.Sentence;
import com.scudata.dm.Context;
import com.scudata.dm.ParamList;
import com.scudata.resources.EngineMessage;

/**
 * 解析函数参数成多叉树
 * @author RunQian
 *
 */
public final class ParamParser {
	// 分隔符节点 ';' ',' ':'
	private static class SymbolParam implements IParam {
		private char level;
		private ArrayList<IParam> paramList = new ArrayList<IParam>(3);

		public SymbolParam(char level) {
			this.level = level;
		}

		public boolean isLeaf() {
			return false;
		}

		public int getSubSize() {
			return paramList.size();
		}

		public IParam getSub(int index) {
			return paramList.get(index);
		}

		public char getType() {
			return level;
		}

		public Expression getLeafExpression() {
			MessageManager mm = EngineMessage.get();
			throw new RQException(mm.getMessage("function.invalidParam"));
		}
		

		public void getAllLeafExpression(ArrayList<Expression> list) {
			for (int i = 0, size = getSubSize(); i < size; ++i) {
				IParam sub = getSub(i);
				if (sub == null) {
					list.add(null);
				} else {
					sub.getAllLeafExpression(list);
				}
			}
		}
		
		/**
		 * 返回表达式数组，只支持单层的参数
		 * @param function 函数名，用于抛出异常
		 * @param canNull 参数是否可空
		 * @return
		 */
		public Expression[] toArray(String function, boolean canNull) {
			int size = getSubSize();
			Expression []exps = new Expression[size];
			for (int i = 0; i < size; ++i) {
				IParam sub = getSub(i);
				if (sub != null) {
					if (sub.isLeaf()) {
						exps[i] = sub.getLeafExpression();
					} else {
						MessageManager mm = EngineMessage.get();
						throw new RQException(function + mm.getMessage("function.invalidParam"));
					}
				} else if (!canNull) {
					MessageManager mm = EngineMessage.get();
					throw new RQException(function + mm.getMessage("function.invalidParam"));
				}
			}
			
			return exps;
		}

		public IParam create(int start, int end) {
			if (end == start + 1) {
				return getSub(start);
			}

			SymbolParam param = new SymbolParam(level);
			for (; start < end; ++start) {
				param.paramList.add(paramList.get(start));
			}
			
			return param;
		}

		void addSub(IParam param) {
			paramList.add(param);
		}

		/**
		 * 返回是否包含指定参数
		 * @param name String
		 * @return boolean
		 */
		public boolean containParam(String name) {
			for (int i = 0, size = getSubSize(); i < size; ++i) {
				IParam sub = getSub(i);
				if (sub != null && sub.containParam(name)) return true;
			}
			return false;
		}

		public void getUsedParams(Context ctx, ParamList resultList) {
			for (int i = 0, size = getSubSize(); i < size; ++i) {
				IParam sub = getSub(i);
				if (sub != null) sub.getUsedParams(ctx, resultList);
			}
		}
		
		public void getUsedFields(Context ctx, List<String> resultList) {
			for (int i = 0, size = getSubSize(); i < size; ++i) {
				IParam sub = getSub(i);
				if (sub != null) sub.getUsedFields(ctx, resultList);
			}
		}
		
		public void getUsedCells(List<INormalCell> resultList) {
			for (int i = 0, size = getSubSize(); i < size; ++i) {
				IParam sub = getSub(i);
				if (sub != null) sub.getUsedCells(resultList);
			}
		}

		public boolean optimize(Context ctx) {
			boolean opt = true;
			for (int i = 0, size = getSubSize(); i < size; ++i) {
				IParam sub = getSub(i);
				if (sub != null && !sub.optimize(ctx)) {
					opt = false;
				}
			}

			return opt;
		}
	}

	// 叶子节点
	private static class LeafParam implements IParam {
		private Expression exp;

		public LeafParam(Expression exp) {
			this.exp = exp;
		}

		public boolean isLeaf() {
			return true;
		}

		public int getSubSize() {
			return 0;
		}

		public IParam getSub(int index) {
			throw new RuntimeException();
		}

		public char getType() {
			return Normal;
		}

		public Expression getLeafExpression() {
			return exp;
		}

		public void getAllLeafExpression(ArrayList<Expression> list) {
			list.add(exp);
		}
		
		/**
		 * 返回表达式数组，只支持单层的参数
		 * @param function 函数名，用于抛出异常
		 * @param canNull 参数是否可空
		 * @return
		 */
		public Expression[] toArray(String function, boolean canNull) {
			return new Expression[]{exp};
		}

		public IParam create(int start, int end) {
			return start > 0 ? this : null;
		}

		/**
		 * 返回是否包含指定参数
		 * @param name String
		 * @return boolean
		 */
		public boolean containParam(String name) {
			return exp.containParam(name);
		}

		public void getUsedParams(Context ctx, ParamList resultList) {
			exp.getUsedParams(ctx, resultList);
		}
		
		public void getUsedFields(Context ctx, List<String> resultList) {
			exp.getUsedFields(ctx, resultList);
		}
		
		public void getUsedCells(List<INormalCell> resultList) {
			exp.getUsedCells(resultList);
		}

		public boolean optimize(Context ctx) {
			exp.optimize(ctx);
			Node home = exp.getHome();
			return home == null || home instanceof Constant;
		}
	}

	/**
	 * 解析层次参数，返回根节点，不做宏替换
	 * @param cs 网格对象
	 * @param ctx 计算上下文
	 * @param paramStr 参数串
	 * @return IParam 参数根节点，没有参数则为空
	 */
	public static IParam parse(String paramStr, ICellSet cs, Context ctx) {
		return parse(paramStr, cs, ctx, IParam.NONE, false, Expression.DoOptimize);
	}

	/**
	 * 解析层次参数，返回根节点
	 * @param cs 网格对象
	 * @param ctx 计算上下文
	 * @param paramStr 参数串
	 * @param doMacro 是否做宏替换，true：做，false：不做
	 * @return IParam 参数根节点，没有参数则为空
	 */
	public static IParam parse(String paramStr, ICellSet cs, Context ctx, boolean doMacro) {
		return parse(paramStr, cs, ctx, IParam.NONE, doMacro, Expression.DoOptimize);
	}

	/**
	 * 解析层次参数，返回根节点
	 * @param paramStr 参数串
	 * @param cs 网格对象
	 * @param ctx 计算上下文
	 * @param doMacro 是否做宏替换，true：做，false：不做
	 * @param doOpt 是否做优化，true：做，false：不做
	 * @return IParam 参数根节点，没有参数则为空
	 */
	public static IParam parse(String paramStr, ICellSet cs, Context ctx, boolean doMacro, boolean doOpt) {
		return parse(paramStr, cs, ctx, IParam.NONE, doMacro, doOpt);
	}

	private static IParam parse(String paramStr, ICellSet cs, Context ctx, char prevLevel, boolean doMacro, boolean doOpt) {
		if (paramStr == null) {
			return null;
		}
		
		paramStr = paramStr.trim();
		if (paramStr.length() == 0) {
			return null;
		}

		// 冒号分隔符下只能是叶子节点
		if (prevLevel == IParam.Colon) {
			return new LeafParam(new Expression(cs, ctx, paramStr, doOpt, doMacro));
		}

		// 找到参数串中存在的分隔符
		char level = getNextLevel(prevLevel);
		while (!hasSeparator(paramStr, level)) {
			// 冒号分隔符下只能是叶子节点
			if (level == IParam.Colon) {
				return new LeafParam(new Expression(cs, ctx, paramStr, doOpt, doMacro));
			} else {
				level = getNextLevel(level);
			}
		}

		// 生成分隔符节点，然后递归解析子节点
		SymbolParam param = new SymbolParam(level);
		ArgumentTokenizer arg = new ArgumentTokenizer(paramStr, level);
		while (arg.hasMoreElements()) {
			param.addSub(parse(arg.nextToken(), cs, ctx, level, doMacro, doOpt));
		}

		return param;
	}

	// 取分隔符的下一层分隔符
	private static char getNextLevel(char prevLevel) {
		switch (prevLevel) {
		case IParam.NONE:
			return IParam.Semicolon;
		case IParam.Semicolon:
			return IParam.Comma;
		case IParam.Comma:
			return IParam.Colon;
		default:
			throw new RQException();
		}
	}

	// 判断参数串中是否存在指定分隔符
	private static boolean hasSeparator(String str, char separator) {
		int len = str.length();
		int index = 0;
		
		while (index < len) {
			// 需要跳过引号、括号和转义符
			char ch = str.charAt(index);

			if (ch == separator) {
				return true;
			} if ( ch == '\\' ) {
				index += 2;
			} else if ( ch == '\"' || ch == '\'' ) {
				int tmp = Sentence.scanQuotation(str, index);
				if (tmp < 0) {
					return false;
				} else {
					index = tmp + 1;
				}
			} else if (ch == '(' ) {
				int tmp = Expression.scanParenthesis(str, index);
				if (tmp < 0) {
					return false;
				} else {
					index = tmp + 1;
				}
			} else if (ch == '[') {
				int tmp = Sentence.scanBracket(str, index);
				if (tmp < 0) {
					return false;
				} else {
					index = tmp + 1;
				}
			} else if (ch == '{') {
				int tmp = Sentence.scanBrace(str, index);
				if (tmp < 0) {
					return false;
				} else {
					index = tmp + 1;
				}
			} else {
				index++;
			}
		}

		return false;
	}
}
