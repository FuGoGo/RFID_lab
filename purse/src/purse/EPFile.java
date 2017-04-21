package purse;

import javacard.framework.JCSystem;
import javacard.framework.Util;

public class EPFile {
	private KeyFile keyfile;
	
	//内部数据元
	private byte[] EP_balance;         //电子钱包余额
	private byte[] EP_offline;         //电子钱包联机交易序号
	private byte[] EP_online;          //电子钱包脱机交易序号
	
	byte keyID;        //密钥版本号
	byte algID;        //算法标识符
	
	//安全系统设计
	private Randgenerator RandData;          //随机数产生
	private PenCipher EnCipher;              //数据加解密方式实现
/**
 * 下面数据是计算时需要用到的临时过程数据	
 */
	//临时计算数据
	//4个字节的临时计算数据
	private byte[] pTemp41;           
	private byte[] pTemp42;
	
	//8个字节的临时计算数据
	private byte[] pTemp81;
	private byte[] pTemp82;
	
	//32个字节的临时计算数据
	private byte[] pTemp16;
	private byte[] pTemp32;
	
	public EPFile(KeyFile keyfile){
		EP_balance = new byte[4];
		Util.arrayFillNonAtomic(EP_balance, (short)0, (short)4, (byte)0x00);
		
		EP_offline = new byte[2];
		Util.arrayFillNonAtomic(EP_offline, (short)0, (short)2, (byte)0x00);
		
		EP_online = new byte[2];
		Util.arrayFillNonAtomic(EP_online, (short)0, (short)2, (byte)0x00);
		
		this.keyfile = keyfile;
		
		pTemp41 = JCSystem.makeTransientByteArray((short)4, JCSystem.CLEAR_ON_DESELECT);
		pTemp42 = JCSystem.makeTransientByteArray((short)4, JCSystem.CLEAR_ON_DESELECT);
		
		pTemp81 = JCSystem.makeTransientByteArray((short)8, JCSystem.CLEAR_ON_DESELECT);
		pTemp82 = JCSystem.makeTransientByteArray((short)8, JCSystem.CLEAR_ON_DESELECT);
		
		pTemp16 = JCSystem.makeTransientByteArray((short)32, JCSystem.CLEAR_ON_DESELECT);
		pTemp32 = JCSystem.makeTransientByteArray((short)32, JCSystem.CLEAR_ON_DESELECT);
		
		RandData = new Randgenerator();
		EnCipher = new PenCipher();
	}
	
	/*
	 * 功能：电子钱包金额的增加
	 * 参数：data 所增加的金额  
	 * 	   flag 是否真正增加电子钱包余额
	 * 返回：圈存后，余额是否超过最大限额
	 */
	public final short increase(byte[] data, boolean flag){
		short i, t1, t2, ads;
		
		ads = (short)0;
		for(i = 3; i >= 0; i --){
			t1 = (short)(EP_balance[(short)i] & 0xFF);
			t2 = (short)(data[i] & 0xFF);
			
			t1 = (short)(t1 + t2 + ads);
			if(flag)
				EP_balance[(short)i] = (byte)(t1 % 256);
			ads = (short)(t1 / 256);
		}
		return ads;
	}
	
	/*
	 * 功能：圈存初始化功能完成
	 * 参数：num 密钥记录号， data 命令报文中的数据段
	 * 返回：0： 圈存初始化命令执行成功     2：圈存超过电子钱包最大限额
	 */
	public final short init4load(short num, byte[] data){
		short length,rc;
		
		//pTemp42用来存放交易金额
		//pTemp81用来存放终端机编号
		Util.arrayCopyNonAtomic(data, (short)1, pTemp42, (short)0, (short)4);  //交易金额
		Util.arrayCopyNonAtomic(data, (short)5, pTemp81, (short)0, (short)6);  //终端机编号
		
		//判断是否超额圈存
		rc = increase(pTemp42, false);
		if(rc != (short)0)
			return (short)2;
		
		/*
		 * 密钥获取
		 * keyfile中显示，取到的秘钥存在pTemp32中，返回伪所查找到的圈存密钥的长度
		 * pTemp32结构：5个字节的密钥头+16个字节的密钥值
		 * 具体结构为前3个byte未知，密钥版本号 1byte，算法标识  1byte，所查找到的圈存密钥16bytes
		 * keyID密钥版本号
		 * algID算法标识符
		 * pTemp16所查找到的圈存密钥
		 */
		length = keyfile.readkey(num, pTemp32);
		keyID = pTemp32[3];
		algID = pTemp32[4];
		Util.arrayCopyNonAtomic(pTemp32, (short)5, pTemp16, (short)0, length);
		
		/*
		 * 产生随机数
		 * RandData.GenerateSecureRnd()函数功能，产生一个4bytes的随机数
		 * RandData.getRndValue(pTemp32, (short)0)功能，将随机数写进pTemp32[0:3]
		 */
		RandData.GenerateSecureRnd();
		RandData.getRndValue(pTemp32, (short)0);
		
		/*
		 * 产生过程密钥——输入数据为伪随机数4bytes||电子钱包联机交易序号2bytes||8000
		 * 在这个部分结束之后，输入数据全部已经写入pTemp32中
		 */
		Util.arrayCopyNonAtomic(EP_online, (short)0, pTemp32, (short)4, (short)2);
		pTemp32[6] = (byte)0x80;
		pTemp32[7] = (byte)0x00;
		
		/*
		 * IC卡生成随机数，利用所查找到的密钥产生过程密钥。
		 * 过程密钥的生成方式，我将在之后的密钥管理中进行说明。
		 * ！其输入的数据为伪随机数||电子钱包联机交易序号||8000
		 * ！密钥为所查找到的圈存密钥
		 * gen_SESPK参数：key 密钥； data 所要加密的数据； dOff 所加密的数据偏移量； dLen 所加密的数据长度； r 加密后的数据； rOff 加密后的数据存储偏移量
		 * 过程秘钥4bytes已经存在pTemp16中，输入数据8bytes已经存在pTemp32中
		 * 加密后的结果是过程秘钥 8bytes，存在pTemp82中
		 */
		EnCipher.gen_SESPK(pTemp16, pTemp32, (short)0, (short)8, pTemp82, (short)0); 
		
		/*
		 * IC卡利用所生成的过程密钥产生MAC1。
		 * 其MAC1的生成方式，我也将在之后的密钥管理中进行说明。
		 * ！其输入的数据为电子钱包余额（交易前）||交易金额||交易类型标识||终端机编号
		 * ！密钥为过程密钥，
		 * 
		 * EP_balance，4bytes，电子钱包余额，存进pTemp32[0:3]
		 * data[1:4], 交易金额, 存进pTemp32[4:7]
		 * 交易类型0x02(p2)，1byte，存进存进pTemp32[8]
		 * data[5:10]，终端机型编号，6byte，存进pTemp32[9:14]
		 * 
		 */
		Util.arrayCopyNonAtomic(EP_balance, (short)0, pTemp32, (short)0, (short)4);   //电子钱包余额
		Util.arrayCopyNonAtomic(data, (short)1, pTemp32, (short)4, (short)4);         //交易金额
		pTemp32[8] = (byte)0x02;                                                      //交易类型标识
		Util.arrayCopyNonAtomic(data, (short)5, pTemp32, (short)9, (short)6);         //终端机编号
		
		/*
		 * 将临时数组内的内容写进data
		 * 对输入数据data 15bytes，使用过程秘钥pTemp82 8bytes加密
		 * 有点担心gmac4的时候，爆数组= =
		 * 加密后的mac值4bytes，存进pTemp41
		 */
		Util.arrayCopyNonAtomic(pTemp32, (short)0, data, (short)0x00, (short)0x0F);
		//gmac4参数：key 密钥; data 所要加密的数据; dl 所要加密的数据长度； mac 所计算得到的MAC和TAC码
		EnCipher.gmac4(pTemp82, pTemp32, (short)0x0F, pTemp41);
		
		/*
		 * 响应数据结构如下
		 * 余额4bytes | 联机交易序列号2bytes | 秘钥版本号1byte | 算法标识1byte | 伪随机数4bytes | mac14bytes
		 * 依次写进data
		 */
		Util.arrayCopyNonAtomic(EP_balance, (short)0, data, (short)0, (short)4);      //电子钱包余额
		Util.arrayCopyNonAtomic(EP_online, (short)0, data,  (short)4, (short)2);      //电子钱包联机交易序号
		data[6] = keyID;                                                              //密钥版本号
		data[7] = algID;                                                              //算法标识
		RandData.getRndValue(data, (short)8);                                         //随机数
		Util.arrayCopyNonAtomic(pTemp41, (short)0, data, (short)12, (short)4);        //mac1
		
		return 0;
	}
	
	/*
	 * 功能：圈存功能的完成
	 * 参数：data 命令报文中的数据段
	 * 返回：0 圈存命令执行成功；1 MAC2校验错误；  2 圈存超过最大限额; 3 密钥未找到
	 */
	public final short load(byte[] data){
		short rc;
		/*
		 * IC卡收到圈存命令后，利用过程密钥生成MAC2。
		 * 其输入数据为交易金额||交易类型标识||终端机编号||交易日期（主机）||交易时间（主机）
		 * 密钥为过程密钥
		 * 与圈存命令传送的MAC2进行比较，如果相同，则MAC2有效
		 * 
		 * 在init4load中，将pTemp42 4bytes用来存放交易金额，将其写进pTemp32[0:3]
		 * 标识符p2为0x02 1byte，将其写进pTemp32[4]
		 * 在init4load中，pTemp81 6bytes用来存放终端机编号，将其写进pTemp32[5:10]
		 * 终端想IC发送的校验指令中，data[0:6]为[日期 | 时间]，将其写进pTemp32[11:17]
		 * 在init4load中，加密后的结果是过程秘钥 8bytes，存在pTemp82中
		 * 对输入pTemp32[0:17]使用过程秘钥pTemp82[0:7]使用gmac4加密，结果写进pTemp41
		 */
		Util.arrayCopyNonAtomic(pTemp42, (short)0, pTemp32, (short)0, (short)4);       //交易金额
		pTemp32[4] = (byte)0x02;                                                       //交易标识
		Util.arrayCopyNonAtomic(pTemp81, (short)0, pTemp32, (short)5, (short)6);       //终端机编号
		Util.arrayCopyNonAtomic(data, (short)0, pTemp32, (short)11, (short)7);         //交易日期与时间
		EnCipher.gmac4(pTemp82, pTemp32, (short)0x12, pTemp41);
		
		/*
		 * 检验MAC2
		 * data[7:10]为MAC2，将其和IC卡自己计算出来的mac2进行校验
		 * 如果一致，IC卡就能够确认终端机的身份了
		 */
		if(Util.arrayCompare(data, (short)7, pTemp41, (short)0, (short)4) != (byte)0x00)
			return (short)1;
		
		//电子钱包数目增加
		rc = increase(pTemp42, true);
		if(rc != (short)0)
			return 2;
		
		/*
		 * IC卡生成TAC码。
		 * TAC码的生成方式和MAC码的生成方式一致。
		 * 输入：电子钱包余额（交易后）||电子钱包联机交易序号（加1前）||交易金额||交易类型标识||终端机编号||交易日期（主机）||交易时间（主机）。
		 * 密钥为TAC密码最左8个字节与TAC密码最右8个字节异或的结果。
		 */
		Util.arrayCopyNonAtomic(EP_balance, (short)0, pTemp32, (short)0, (short)4);    //电子钱包余额
		Util.arrayCopyNonAtomic(EP_online, (short)0, pTemp32, (short)4, (short)2);     //电子钱包联机交易序号
		Util.arrayCopyNonAtomic(pTemp42, (short)0, pTemp32, (short)6, (short)4);       //交易金额
		pTemp32[10] = (byte)0x02;                                                      //交易类型
		Util.arrayCopyNonAtomic(pTemp81, (short)0, pTemp32, (short)11, (short)6);      //终端机编号
		Util.arrayCopyNonAtomic(data, (short)0, pTemp32, (short)17, (short)7);         //交易日期与时间
		
		//联机交易序号加1
		rc = Util.makeShort(EP_online[0], EP_online[1]);
		rc ++;
		if(rc > (short)256)
			rc = (short)1;
		Util.setShort(EP_online, (short)0, rc);
		
		/*
		 * TAC的计算
		 * 获取秘钥16bytes，存进pTemp16
		 * keyfile中显示，取到的秘钥存在pTemp16中，返回伪所查找到的圈存密钥的长度
		 * pTemp16结构：5个字节的密钥头+16个字节的密钥值
		 * 具体结构为前3个byte未知，密钥版本号 1byte，算法标识  1byte，所查找到的圈存密钥16bytes
		 */
		short length, num;
		num = keyfile.findKeyByType((byte)0x34);//为什么只找0x34？？？？？
		length = keyfile.readkey(num, pTemp16);
		
		if(length == 0)
			return (short)3;
		
		//取秘钥的前8位，写进pTemp82[0:7]，覆盖掉过程秘钥
		Util.arrayCopyNonAtomic(pTemp16, (short)5, pTemp82, (short)0, (short)8);
		//key再搞搞事情，计算tac，写进data
		EnCipher.xorblock8(pTemp82, pTemp16, (short)13);
		EnCipher.gmac4(pTemp82, pTemp32, (short)0x18, data);
		
		return (short)0;
	}
	/*
	 * 功能：电子钱包金额减少
	 * 参数：data 消费的金额； flag 是否真正扣减电子钱包余额
	 * 返回： 消费是否超额
	 */
	public final short decrease(byte[] data, boolean flag){
		return 0;
	}
		
	/*
	 * 功能：消费初始化命令的完成
	 * 参数：num 密钥记录号； data 命令报文中的数据段
	 * 返回：0 命令执行成功；2 消费超额
	 */
	public final short init4purchase(short num, byte[] data){
		return 0;
		
	}
	/*
	 * 功能：消费命令的实现
	 * 参数：data 命令报文中的数据段
	 * 返回：0 命令执行成功； 1 MAC校验错误 2 消费超额； 3 密钥未找到
	 */
	public final short purchase(byte[] data){
		return 0;
	}
	/*
	 * 功能：电子钱包余额获取
	 * 参数：data 电子钱包余额的缓冲区
	 * 返回： 0
	 */
	public final short get_balance(byte[] data){
		return 0;
	}
}
