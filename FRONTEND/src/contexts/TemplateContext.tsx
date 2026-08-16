/* 이 파일은 "지금 어떤 템플릿(A/B/C/D)이 선택되어 있는지"를
   앱 전체에서 공유하기 위한 전역 상태(Context)를 정의한다.

왜 Context API를 쓰는가?
 - Redux 같은 추가 라이브러리 설치 없이 리액트 자체 기능만으로 구현 가능
 - 이 프로젝트 규모(템플릿 선택값 1개 공유)에는 Context로 충분함
 - 상태가 커지면(예: 여러 화면의 폼 데이터까지 전역화) 그때 Redux/Zustand 등으로 교체 검토 */

import {
  createContext,
  useContext,
  useState,
  useCallback,
  useMemo,
  type ReactNode,
} from 'react';
import {
  TEMPLATES,
  type Template,
  type TemplateId,
} from '../components/TemplateSelector/templateData';

interface TemplateContextValue {
  templates: Template[];
  selectedTemplateId: TemplateId | null;
  selectedTemplate: Template | null;
  lastChangedAt: number | null;
  setSelectedTemplateId: (templateId: TemplateId) => void;
}

const TemplateContext = createContext<TemplateContextValue | undefined>(
  undefined,
);

interface TemplateProviderProps {
  children: ReactNode;
}

/**
 * TemplateProvider
 * App.tsx(또는 최상위 컴포넌트)에서 이 Provider로 하위 트리를 감싸면
 * 그 아래 모든 컴포넌트가 useTemplate() 훅으로 선택된 템플릿 값을 읽고 바꿀 수 있다.
 */
export function TemplateProvider({ children }: TemplateProviderProps) {
  // 완료 기준 1: "선택 시 전역 상태 값 갱신"
  const [selectedTemplateId, setSelectedTemplateIdState] =
    useState<TemplateId | null>(null);

  // 완료 기준 2: "선택값 변경 시 미리보기 반영용 상태 트리거"
  // selectedTemplateId 자체가 바뀌면 이 값을 구독하는 모든 컴포넌트(예: 미리보기 패널)가
  // 자동으로 리렌더링되지만, "언제 바뀌었는지"를 명시적으로 구분해야 하는 화면(애니메이션,
  // 로그 등)을 위해 변경 시각(lastChangedAt)을 별도 트리거 값으로 함께 제공한다.
  const [lastChangedAt, setLastChangedAt] = useState<number | null>(null);

  const setSelectedTemplateId = useCallback((templateId: TemplateId) => {
    setSelectedTemplateIdState(templateId);
    setLastChangedAt(Date.now());
  }, []);

  // 완료 기준 3: "새로고침 전까지 값 유지"
  // -> 의도적으로 localStorage/sessionStorage에 저장하지 않는다.
  //    useState는 브라우저 새로고침 시 초기화되므로 이 요구사항을 그대로 만족한다.
  //    (나중에 "새로고침해도 유지"로 요구사항이 바뀌면 이 부분만 localStorage 연동으로 교체)

  const selectedTemplate = useMemo<Template | null>(
    () => TEMPLATES.find((t) => t.id === selectedTemplateId) ?? null,
    [selectedTemplateId],
  );

  const value = useMemo<TemplateContextValue>(
    () => ({
      templates: TEMPLATES,
      selectedTemplateId,
      selectedTemplate,
      lastChangedAt,
      setSelectedTemplateId,
    }),
    [
      selectedTemplateId,
      selectedTemplate,
      lastChangedAt,
      setSelectedTemplateId,
    ],
  );

  return (
    <TemplateContext.Provider value={value}>
      {children}
    </TemplateContext.Provider>
  );
}

// 하위 컴포넌트에서 선택된 템플릿을 읽거나 바꿀 때 사용하는 훅.
export function useTemplate(): TemplateContextValue {
  const ctx = useContext(TemplateContext);
  if (ctx === undefined) {
    throw new Error(
      'useTemplate()은 반드시 <TemplateProvider> 내부에서 호출해야 합니다.',
    );
  }
  return ctx;
}
