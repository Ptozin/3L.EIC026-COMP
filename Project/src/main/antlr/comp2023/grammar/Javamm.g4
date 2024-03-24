grammar Javamm;

@header {
    package pt.up.fe.comp2023;
}

INTEGER
    : '0'
    | [1-9][0-9]*
    ;

ID : [a-zA-Z_$][a-zA-Z_0-9$]* ;

WS : ( [ \t\r\n\f] | COMMENT)+ -> skip ;

COMMENT
    : '/*' .*? '*/'
    | '//' ~('\r' | '\n')*
    ;

program : (importDeclaration)* classDeclaration EOF ;

importDeclaration : 'import' name=ID (subPackage)* ';' ;

subPackage : '.'name=ID ;

classDeclaration : 'class' className=ID ('extends' extendName=ID)? '{' (varDeclaration | methodDeclaration)* '}' ;

varDeclaration : type name=ID ';' ;

methodDeclaration
    : ('public')? typeReturn methodName=ID '(' (parameter (',' parameter)* )? ')' '{' ( varDeclaration
        )* ( statement )* ('return' expression ';')? '}' #Method
    | ('public')? 'static' 'void' 'main' '(' mainArg argName=ID ')' '{' ( varDeclaration
        )* ( statement )* '}' #MainMethod
    ;

mainArg
    : 'String' '[' ']' #ArgumentStringArray
    ;

parameter : type name=ID ;

typeReturn
    : 'void' #ReturnVoid
    | type #ReturnType
    ;

type
    : value='boolean' #VarType
    | value='byte' #VarType
    | value='short' #VarType
    | value='long' #VarType
    | value='float' #VarType
    | value='double' #VarType
    | value='char' #VarType
    | value='int' #VarType
    | value='String' #VarType
    | value=ID #VarType
    | type '[' ']' #Array
    ;

statement
    : '{' (statement)* '}' #StatementBlock
    | 'if' '(' expression ')' statement ('else' statement)? #IfStatement
    | 'while' '(' expression ')' statement #WhileStatement
    | expression ';' #ExpressionStatement
    | var=ID '=' expression ';' #Assignment
    | var=ID '[' expression ']' '=' expression ';' #IndexAssignment
    ;

expression
    : '(' expression ')' #Parenthesis
    | '!' expression #Negation
    | 'new' 'int' '[' expression ']' #NewIntArray
    | 'new' className=ID '(' ')' #NewClass
    | expression '[' expression ']' #IndexAccess
    | expression '.' expression '(' ( expression ( ','expression )* )? ')' #MethodCall
    | expression '.' 'length' #MethodLength
    | expression op=('*' | '/' | '%') expression #BinaryOp
    | expression op=('+' | '-') expression #BinaryOp
    | expression op=('<' | '>' | '<=' | '=>') expression #Comparation
    | expression op=('==' | '!=') expression #Comparation
    | expression op='&&' expression #BinaryOp
    | expression op='||' expression #BinaryOp
    | value=INTEGER #Integer
    | 'true' #True
    | 'false' #False
    | value=ID #Identifier
    | 'this' #This
    ;